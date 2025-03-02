/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* TO DO:
 *   1.  Perhaps keep several copies of the encrypted key, in case something
 *       goes horribly wrong?
 *
 */

#include <sys/types.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <ctype.h>
#include <fcntl.h>
#include <inttypes.h>
#include <unistd.h>
#include <stdio.h>
#include <sys/ioctl.h>
#include <linux/dm-ioctl.h>
#include <libgen.h>
#include <stdlib.h>
#include <sys/param.h>
#include <string.h>
#include <sys/mount.h>
#include <openssl/evp.h>
#include <openssl/sha.h>
#include <errno.h>
#include <ext4.h>
#include <linux/kdev_t.h>
#include <fs_mgr.h>
#include <time.h>
#include <math.h>
#include "cryptfs.h"
#define LOG_TAG "Cryptfs"
#include "cutils/log.h"
#include "cutils/properties.h"
#include "cutils/android_reboot.h"
#include "hardware_legacy/power.h"
#include <logwrap/logwrap.h>
#include "VolumeManager.h"
#include "VoldUtil.h"
#include "crypto_scrypt.h"
#include "Ext4Crypt.h"
#include "ext4_crypt_init_extensions.h"
#include "ext4_utils.h"
#include "f2fs_sparseblock.h"
#include "CheckBattery.h"
#include "Process.h"
#include <sys/stat.h>
#include <dirent.h>

#include <hardware/keymaster0.h>
#include <hardware/keymaster1.h>

#define UNUSED __attribute__((unused))

#define UNUSED __attribute__((unused))

#ifdef CONFIG_HW_DISK_ENCRYPTION
#include "cryptfs_hw.h"
#endif

#define DM_CRYPT_BUF_SIZE 4096

#define HASH_COUNT 2000
#define KEY_LEN_BYTES 16
#define IV_LEN_BYTES 16

#define KEY_IN_FOOTER  "footer"

#define DEFAULT_PASSWORD "default_password"

#define EXT4_FS 1
#define F2FS_FS 2

#define TABLE_LOAD_RETRIES 10

#define RSA_KEY_SIZE 2048
#define RSA_KEY_SIZE_BYTES (RSA_KEY_SIZE / 8)
#define RSA_EXPONENT 0x10001
#define KEYMASTER_CRYPTFS_RATE_LIMIT 1  // Maximum one try per second

#define RETRY_MOUNT_ATTEMPTS 10
#define RETRY_MOUNT_DELAY_SECONDS 1

#ifndef BLKFLSBUF
#define BLKFLSBUF  _IO(0x12,97)	/* flush buffer cache */
#endif

char *me = "cryptfs";

static unsigned char saved_master_key[KEY_LEN_BYTES];
static char *saved_mount_point;
static int  master_key_saved = 0;
static struct crypt_persist_data *persist_data = NULL;

static int keymaster_init(keymaster0_device_t **keymaster0_dev,
                          keymaster1_device_t **keymaster1_dev)
{
    int rc;

    const hw_module_t* mod;
    rc = hw_get_module_by_class(KEYSTORE_HARDWARE_MODULE_ID, NULL, &mod);
    if (rc) {
        ALOGE("could not find any keystore module");
        goto err;
    }

    SLOGI("keymaster module name is %s", mod->name);
    SLOGI("keymaster version is %d", mod->module_api_version);

    *keymaster0_dev = NULL;
    *keymaster1_dev = NULL;
    if (mod->module_api_version == KEYMASTER_MODULE_API_VERSION_1_0) {
        SLOGI("Found keymaster1 module, using keymaster1 API.");
        rc = keymaster1_open(mod, keymaster1_dev);
    } else {
        SLOGI("Found keymaster0 module, using keymaster0 API.");
        rc = keymaster0_open(mod, keymaster0_dev);
    }

    if (rc) {
        ALOGE("could not open keymaster device in %s (%s)",
              KEYSTORE_HARDWARE_MODULE_ID, strerror(-rc));
        goto err;
    }

    return 0;

err:
    *keymaster0_dev = NULL;
    *keymaster1_dev = NULL;
    return rc;
}

/* Should we use keymaster? */
static int keymaster_check_compatibility()
{
    keymaster0_device_t *keymaster0_dev = 0;
    keymaster1_device_t *keymaster1_dev = 0;
    int rc = 0;

    if (keymaster_init(&keymaster0_dev, &keymaster1_dev)) {
        SLOGE("Failed to init keymaster");
        rc = -1;
        goto out;
    }

    if (keymaster1_dev) {
        rc = 1;
        goto out;
    }

    // TODO(swillden): Check to see if there's any reason to require v0.3.  I think v0.1 and v0.2
    // should work.
    if (keymaster0_dev->common.module->module_api_version
            < KEYMASTER_MODULE_API_VERSION_0_3) {
        rc = 0;
        goto out;
    }

    if (!(keymaster0_dev->flags & KEYMASTER_SOFTWARE_ONLY) &&
        (keymaster0_dev->flags & KEYMASTER_BLOBS_ARE_STANDALONE)) {
        rc = 1;
    }

out:
    if (keymaster1_dev) {
        keymaster1_close(keymaster1_dev);
    }
    if (keymaster0_dev) {
        keymaster0_close(keymaster0_dev);
    }
    return rc;
}

/* Create a new keymaster key and store it in this footer */
static int keymaster_create_key(struct crypt_mnt_ftr *ftr)
{
    uint8_t* key = 0;
    keymaster0_device_t *keymaster0_dev = 0;
    keymaster1_device_t *keymaster1_dev = 0;

    if (keymaster_init(&keymaster0_dev, &keymaster1_dev)) {
        SLOGE("Failed to init keymaster");
        return -1;
    }

    int rc = 0;
    size_t key_size = 0;
    if (keymaster1_dev) {
        keymaster_key_param_t params[] = {
            /* Algorithm & size specifications.  Stick with RSA for now.  Switch to AES later. */
            keymaster_param_enum(KM_TAG_ALGORITHM, KM_ALGORITHM_RSA),
            keymaster_param_int(KM_TAG_KEY_SIZE, RSA_KEY_SIZE),
            keymaster_param_long(KM_TAG_RSA_PUBLIC_EXPONENT, RSA_EXPONENT),

	    /* The only allowed purpose for this key is signing. */
	    keymaster_param_enum(KM_TAG_PURPOSE, KM_PURPOSE_SIGN),

            /* Padding & digest specifications. */
            keymaster_param_enum(KM_TAG_PADDING, KM_PAD_NONE),
            keymaster_param_enum(KM_TAG_DIGEST, KM_DIGEST_NONE),

            /* Require that the key be usable in standalone mode.  File system isn't available. */
            keymaster_param_enum(KM_TAG_BLOB_USAGE_REQUIREMENTS, KM_BLOB_STANDALONE),

            /* No auth requirements, because cryptfs is not yet integrated with gatekeeper. */
            keymaster_param_bool(KM_TAG_NO_AUTH_REQUIRED),

            /* Rate-limit key usage attempts, to rate-limit brute force */
            keymaster_param_int(KM_TAG_MIN_SECONDS_BETWEEN_OPS, KEYMASTER_CRYPTFS_RATE_LIMIT),
        };
        keymaster_key_param_set_t param_set = { params, sizeof(params)/sizeof(*params) };
        keymaster_key_blob_t key_blob;
        keymaster_error_t error = keymaster1_dev->generate_key(keymaster1_dev, &param_set,
                                                               &key_blob,
                                                               NULL /* characteristics */);
        if (error != KM_ERROR_OK) {
            SLOGE("Failed to generate keymaster1 key, error %d", error);
            rc = -1;
            goto out;
        }

        key = (uint8_t*)key_blob.key_material;
        key_size = key_blob.key_material_size;
    }
    else if (keymaster0_dev) {
        keymaster_rsa_keygen_params_t params;
        memset(&params, '\0', sizeof(params));
        params.public_exponent = RSA_EXPONENT;
        params.modulus_size = RSA_KEY_SIZE;

        if (keymaster0_dev->generate_keypair(keymaster0_dev, TYPE_RSA, &params,
                                             &key, &key_size)) {
            SLOGE("Failed to generate keypair");
            rc = -1;
            goto out;
        }
    } else {
        SLOGE("Cryptfs bug: keymaster_init succeeded but didn't initialize a device");
        rc = -1;
        goto out;
    }

    if (key_size > KEYMASTER_BLOB_SIZE) {
        SLOGE("Keymaster key too large for crypto footer");
        rc = -1;
        goto out;
    }

    memcpy(ftr->keymaster_blob, key, key_size);
    ftr->keymaster_blob_size = key_size;

out:
    if (keymaster0_dev)
        keymaster0_close(keymaster0_dev);
    if (keymaster1_dev)
        keymaster1_close(keymaster1_dev);
    free(key);
    return rc;
}

/* This signs the given object using the keymaster key. */
static int keymaster_sign_object(struct crypt_mnt_ftr *ftr,
                                 const unsigned char *object,
                                 const size_t object_size,
                                 unsigned char **signature,
                                 size_t *signature_size)
{
    int rc = 0;
    keymaster0_device_t *keymaster0_dev = 0;
    keymaster1_device_t *keymaster1_dev = 0;
    if (keymaster_init(&keymaster0_dev, &keymaster1_dev)) {
        SLOGE("Failed to init keymaster");
        rc = -1;
        goto out;
    }

    unsigned char to_sign[RSA_KEY_SIZE_BYTES];
    size_t to_sign_size = sizeof(to_sign);
    memset(to_sign, 0, RSA_KEY_SIZE_BYTES);

    // To sign a message with RSA, the message must satisfy two
    // constraints:
    //
    // 1. The message, when interpreted as a big-endian numeric value, must
    //    be strictly less than the public modulus of the RSA key.  Note
    //    that because the most significant bit of the public modulus is
    //    guaranteed to be 1 (else it's an (n-1)-bit key, not an n-bit
    //    key), an n-bit message with most significant bit 0 always
    //    satisfies this requirement.
    //
    // 2. The message must have the same length in bits as the public
    //    modulus of the RSA key.  This requirement isn't mathematically
    //    necessary, but is necessary to ensure consistency in
    //    implementations.
    switch (ftr->kdf_type) {
        case KDF_SCRYPT_KEYMASTER:
            // This ensures the most significant byte of the signed message
            // is zero.  We could have zero-padded to the left instead, but
            // this approach is slightly more robust against changes in
            // object size.  However, it's still broken (but not unusably
            // so) because we really should be using a proper deterministic
            // RSA padding function, such as PKCS1.
            memcpy(to_sign + 1, object, min(RSA_KEY_SIZE_BYTES - 1, object_size));
            SLOGI("Signing safely-padded object");
            break;
        default:
            SLOGE("Unknown KDF type %d", ftr->kdf_type);
            rc = -1;
            goto out;
    }

    if (keymaster0_dev) {
        keymaster_rsa_sign_params_t params;
        params.digest_type = DIGEST_NONE;
        params.padding_type = PADDING_NONE;

        rc = keymaster0_dev->sign_data(keymaster0_dev,
                                      &params,
                                      ftr->keymaster_blob,
                                      ftr->keymaster_blob_size,
                                      to_sign,
                                      to_sign_size,
                                      signature,
                                      signature_size);
        goto out;
    } else if (keymaster1_dev) {
        keymaster_key_blob_t key = { ftr->keymaster_blob, ftr->keymaster_blob_size };
        keymaster_key_param_t params[] = {
            keymaster_param_enum(KM_TAG_PADDING, KM_PAD_NONE),
            keymaster_param_enum(KM_TAG_DIGEST, KM_DIGEST_NONE),
        };
        keymaster_key_param_set_t param_set = { params, sizeof(params)/sizeof(*params) };
        keymaster_operation_handle_t op_handle;
        keymaster_error_t error = keymaster1_dev->begin(keymaster1_dev, KM_PURPOSE_SIGN, &key,
                                                        &param_set, NULL /* out_params */,
                                                        &op_handle);
        if (error == KM_ERROR_KEY_RATE_LIMIT_EXCEEDED) {
            // Key usage has been rate-limited.  Wait a bit and try again.
            sleep(KEYMASTER_CRYPTFS_RATE_LIMIT);
            error = keymaster1_dev->begin(keymaster1_dev, KM_PURPOSE_SIGN, &key,
                                          &param_set, NULL /* out_params */,
                                          &op_handle);
        }
        if (error != KM_ERROR_OK) {
            SLOGE("Error starting keymaster signature transaction: %d", error);
            rc = -1;
            goto out;
        }

        keymaster_blob_t input = { to_sign, to_sign_size };
        size_t input_consumed;
        error = keymaster1_dev->update(keymaster1_dev, op_handle, NULL /* in_params */,
                                       &input, &input_consumed, NULL /* out_params */,
                                       NULL /* output */);
        if (error != KM_ERROR_OK) {
            SLOGE("Error sending data to keymaster signature transaction: %d", error);
            rc = -1;
            goto out;
        }
        if (input_consumed != to_sign_size) {
            // This should never happen.  If it does, it's a bug in the keymaster implementation.
            SLOGE("Keymaster update() did not consume all data.");
            keymaster1_dev->abort(keymaster1_dev, op_handle);
            rc = -1;
            goto out;
        }

        keymaster_blob_t tmp_sig;
        error = keymaster1_dev->finish(keymaster1_dev, op_handle, NULL /* in_params */,
                                       NULL /* verify signature */, NULL /* out_params */,
                                       &tmp_sig);
        if (error != KM_ERROR_OK) {
            SLOGE("Error finishing keymaster signature transaction: %d", error);
            rc = -1;
            goto out;
        }

        *signature = (uint8_t*)tmp_sig.data;
        *signature_size = tmp_sig.data_length;
    } else {
        SLOGE("Cryptfs bug: keymaster_init succeded but didn't initialize a device.");
        rc = -1;
        goto out;
    }

    out:
        if (keymaster1_dev)
            keymaster1_close(keymaster1_dev);
        if (keymaster0_dev)
            keymaster0_close(keymaster0_dev);

        return rc;
}

/* Store password when userdata is successfully decrypted and mounted.
 * Cleared by cryptfs_clear_password
 *
 * To avoid a double prompt at boot, we need to store the CryptKeeper
 * password and pass it to KeyGuard, which uses it to unlock KeyStore.
 * Since the entire framework is torn down and rebuilt after encryption,
 * we have to use a daemon or similar to store the password. Since vold
 * is secured against IPC except from system processes, it seems a reasonable
 * place to store this.
 *
 * password should be cleared once it has been used.
 *
 * password is aged out after password_max_age_seconds seconds.
 */
static char* password = 0;
static int password_expiry_time = 0;
static const int password_max_age_seconds = 60;

extern struct fstab *fstab;

enum RebootType {reboot, recovery, shutdown};
static void cryptfs_reboot(enum RebootType rt)
{
  switch(rt) {
      case reboot:
          property_set(ANDROID_RB_PROPERTY, "reboot");
          break;

      case recovery:
          property_set(ANDROID_RB_PROPERTY, "reboot,recovery");
          break;

      case shutdown:
          property_set(ANDROID_RB_PROPERTY, "shutdown");
          break;
    }

    sleep(20);

    /* Shouldn't get here, reboot should happen before sleep times out */
    return;
}

static void ioctl_init(struct dm_ioctl *io, size_t dataSize, const char *name, unsigned flags)
{
    memset(io, 0, dataSize);
    io->data_size = dataSize;
    io->data_start = sizeof(struct dm_ioctl);
    io->version[0] = 4;
    io->version[1] = 0;
    io->version[2] = 0;
    io->flags = flags;
    if (name) {
        strlcpy(io->name, name, sizeof(io->name));
    }
}

/**
 * Gets the default device scrypt parameters for key derivation time tuning.
 * The parameters should lead to about one second derivation time for the
 * given device.
 */
static void get_device_scrypt_params(struct crypt_mnt_ftr *ftr) {
    const int default_params[] = SCRYPT_DEFAULTS;
    int params[] = SCRYPT_DEFAULTS;
    char paramstr[PROPERTY_VALUE_MAX];
    char *token;
    char *saveptr;
    int i;

    property_get(SCRYPT_PROP, paramstr, "");
    if (paramstr[0] != '\0') {
        /*
         * The token we're looking for should be three integers separated by
         * colons (e.g., "12:8:1"). Scan the property to make sure it matches.
         */
        for (i = 0, token = strtok_r(paramstr, ":", &saveptr);
                token != NULL && i < 3;
                i++, token = strtok_r(NULL, ":", &saveptr)) {
            char *endptr;
            params[i] = strtol(token, &endptr, 10);

            /*
             * Check that there was a valid number and it's 8-bit. If not,
             * break out and the end check will take the default values.
             */
            if ((*token == '\0') || (*endptr != '\0') || params[i] < 0 || params[i] > 255) {
                break;
            }
        }

        /*
         * If there were not enough tokens or a token was malformed (not an
         * integer), it will end up here and the default parameters can be
         * taken.
         */
        if ((i != 3) || (token != NULL)) {
            SLOGW("bad scrypt parameters '%s' should be like '12:8:1'; using defaults", paramstr);
            memcpy(params, default_params, sizeof(params));
        }
    }

    ftr->N_factor = params[0];
    ftr->r_factor = params[1];
    ftr->p_factor = params[2];
}

static unsigned int get_fs_size(char *dev)
{
    int fd, block_size;
    struct ext4_super_block sb;
    off64_t len;

    if ((fd = open(dev, O_RDONLY|O_CLOEXEC)) < 0) {
        SLOGE("Cannot open device to get filesystem size ");
        return 0;
    }

    if (lseek64(fd, 1024, SEEK_SET) < 0) {
        SLOGE("Cannot seek to superblock");
        return 0;
    }

    if (read(fd, &sb, sizeof(sb)) != sizeof(sb)) {
        SLOGE("Cannot read superblock");
        return 0;
    }

    close(fd);

    if (le32_to_cpu(sb.s_magic) != EXT4_SUPER_MAGIC) {
        SLOGE("Not a valid ext4 superblock");
        return 0;
    }
    block_size = 1024 << sb.s_log_block_size;
    /* compute length in bytes */
    len = ( ((off64_t)sb.s_blocks_count_hi << 32) + sb.s_blocks_count_lo) * block_size;

    /* return length in sectors */
    return (unsigned int) (len / 512);
}

static int get_crypt_ftr_info(char **metadata_fname, off64_t *off)
{
  static int cached_data = 0;
  static off64_t cached_off = 0;
  static char cached_metadata_fname[PROPERTY_VALUE_MAX] = "";
  int fd;
  char key_loc[PROPERTY_VALUE_MAX];
  char real_blkdev[PROPERTY_VALUE_MAX];
  int rc = -1;

  if (!cached_data) {
    fs_mgr_get_crypt_info(fstab, key_loc, real_blkdev, sizeof(key_loc));

    if (!strcmp(key_loc, KEY_IN_FOOTER)) {
      if ( (fd = open(real_blkdev, O_RDWR|O_CLOEXEC)) < 0) {
        SLOGE("Cannot open real block device %s\n", real_blkdev);
        return -1;
      }

      unsigned long nr_sec = 0;
      get_blkdev_size(fd, &nr_sec);
      if (nr_sec != 0) {
        /* If it's an encrypted Android partition, the last 16 Kbytes contain the
         * encryption info footer and key, and plenty of bytes to spare for future
         * growth.
         */
        strlcpy(cached_metadata_fname, real_blkdev, sizeof(cached_metadata_fname));
        cached_off = ((off64_t)nr_sec * 512) - CRYPT_FOOTER_OFFSET;
        cached_data = 1;
      } else {
        SLOGE("Cannot get size of block device %s\n", real_blkdev);
      }
      close(fd);
    } else {
      strlcpy(cached_metadata_fname, key_loc, sizeof(cached_metadata_fname));
      cached_off = 0;
      cached_data = 1;
    }
  }

  if (cached_data) {
    if (metadata_fname) {
        *metadata_fname = cached_metadata_fname;
    }
    if (off) {
        *off = cached_off;
    }
    rc = 0;
  }

  return rc;
}

/* key or salt can be NULL, in which case just skip writing that value.  Useful to
 * update the failed mount count but not change the key.
 */
static int put_crypt_ftr_and_key(struct crypt_mnt_ftr *crypt_ftr)
{
  int fd;
  unsigned int cnt;
  /* starting_off is set to the SEEK_SET offset
   * where the crypto structure starts
   */
  off64_t starting_off;
  int rc = -1;
  char *fname = NULL;
  struct stat statbuf;

  if (get_crypt_ftr_info(&fname, &starting_off)) {
    SLOGE("Unable to get crypt_ftr_info\n");
    return -1;
  }
  if (fname[0] != '/') {
    SLOGE("Unexpected value for crypto key location\n");
    return -1;
  }
  if ( (fd = open(fname, O_RDWR | O_CREAT|O_CLOEXEC, 0600)) < 0) {
    SLOGE("Cannot open footer file %s for put\n", fname);
    return -1;
  }

  /* Seek to the start of the crypt footer */
  if (lseek64(fd, starting_off, SEEK_SET) == -1) {
    SLOGE("Cannot seek to real block device footer\n");
    goto errout;
  }

  if ((cnt = write(fd, crypt_ftr, sizeof(struct crypt_mnt_ftr))) != sizeof(struct crypt_mnt_ftr)) {
    SLOGE("Cannot write real block device footer\n");
    goto errout;
  }

  fstat(fd, &statbuf);
  /* If the keys are kept on a raw block device, do not try to truncate it. */
  if (S_ISREG(statbuf.st_mode)) {
    if (ftruncate(fd, 0x4000)) {
      SLOGE("Cannot set footer file size\n");
      goto errout;
    }
  }

  /* Success! */
  rc = 0;

errout:
  close(fd);
  return rc;

}

static inline int unix_read(int  fd, void*  buff, int  len)
{
    return TEMP_FAILURE_RETRY(read(fd, buff, len));
}

static inline int unix_write(int  fd, const void*  buff, int  len)
{
    return TEMP_FAILURE_RETRY(write(fd, buff, len));
}

static void init_empty_persist_data(struct crypt_persist_data *pdata, int len)
{
    memset(pdata, 0, len);
    pdata->persist_magic = PERSIST_DATA_MAGIC;
    pdata->persist_valid_entries = 0;
}

/* A routine to update the passed in crypt_ftr to the lastest version.
 * fd is open read/write on the device that holds the crypto footer and persistent
 * data, crypt_ftr is a pointer to the struct to be updated, and offset is the
 * absolute offset to the start of the crypt_mnt_ftr on the passed in fd.
 */
static void upgrade_crypt_ftr(int fd, struct crypt_mnt_ftr *crypt_ftr, off64_t offset)
{
    int orig_major = crypt_ftr->major_version;
    int orig_minor = crypt_ftr->minor_version;

    if ((crypt_ftr->major_version == 1) && (crypt_ftr->minor_version == 0)) {
        struct crypt_persist_data *pdata;
        off64_t pdata_offset = offset + CRYPT_FOOTER_TO_PERSIST_OFFSET;

        SLOGW("upgrading crypto footer to 1.1");

        pdata = malloc(CRYPT_PERSIST_DATA_SIZE);
        if (pdata == NULL) {
            SLOGE("Cannot allocate persisent data\n");
            return;
        }
        memset(pdata, 0, CRYPT_PERSIST_DATA_SIZE);

        /* Need to initialize the persistent data area */
        if (lseek64(fd, pdata_offset, SEEK_SET) == -1) {
            SLOGE("Cannot seek to persisent data offset\n");
            free(pdata);
            return;
        }
        /* Write all zeros to the first copy, making it invalid */
        unix_write(fd, pdata, CRYPT_PERSIST_DATA_SIZE);

        /* Write a valid but empty structure to the second copy */
        init_empty_persist_data(pdata, CRYPT_PERSIST_DATA_SIZE);
        unix_write(fd, pdata, CRYPT_PERSIST_DATA_SIZE);

        /* Update the footer */
        crypt_ftr->persist_data_size = CRYPT_PERSIST_DATA_SIZE;
        crypt_ftr->persist_data_offset[0] = pdata_offset;
        crypt_ftr->persist_data_offset[1] = pdata_offset + CRYPT_PERSIST_DATA_SIZE;
        crypt_ftr->minor_version = 1;
        free(pdata);
    }

    if ((crypt_ftr->major_version == 1) && (crypt_ftr->minor_version == 1)) {
        SLOGW("upgrading crypto footer to 1.2");
        /* But keep the old kdf_type.
         * It will get updated later to KDF_SCRYPT after the password has been verified.
         */
        crypt_ftr->kdf_type = KDF_PBKDF2;
        get_device_scrypt_params(crypt_ftr);
        crypt_ftr->minor_version = 2;
    }

    if ((crypt_ftr->major_version == 1) && (crypt_ftr->minor_version == 2)) {
        SLOGW("upgrading crypto footer to 1.3");
        crypt_ftr->crypt_type = CRYPT_TYPE_PASSWORD;
        crypt_ftr->minor_version = 3;
    }

    if ((orig_major != crypt_ftr->major_version) || (orig_minor != crypt_ftr->minor_version)) {
        if (lseek64(fd, offset, SEEK_SET) == -1) {
            SLOGE("Cannot seek to crypt footer\n");
            return;
        }
        unix_write(fd, crypt_ftr, sizeof(struct crypt_mnt_ftr));
    }
}


static int get_crypt_ftr_and_key(struct crypt_mnt_ftr *crypt_ftr)
{
  int fd;
  unsigned int cnt;
  off64_t starting_off;
  int rc = -1;
  char *fname = NULL;
  struct stat statbuf;

  if (get_crypt_ftr_info(&fname, &starting_off)) {
    SLOGE("Unable to get crypt_ftr_info\n");
    return -1;
  }
  if (fname[0] != '/') {
    SLOGE("Unexpected value for crypto key location\n");
    return -1;
  }
  if ( (fd = open(fname, O_RDWR|O_CLOEXEC)) < 0) {
    SLOGE("Cannot open footer file %s for get\n", fname);
    return -1;
  }

  /* Make sure it's 16 Kbytes in length */
  fstat(fd, &statbuf);
  if (S_ISREG(statbuf.st_mode) && (statbuf.st_size != 0x4000)) {
    SLOGE("footer file %s is not the expected size!\n", fname);
    goto errout;
  }

  /* Seek to the start of the crypt footer */
  if (lseek64(fd, starting_off, SEEK_SET) == -1) {
    SLOGE("Cannot seek to real block device footer\n");
    goto errout;
  }

  if ( (cnt = read(fd, crypt_ftr, sizeof(struct crypt_mnt_ftr))) != sizeof(struct crypt_mnt_ftr)) {
    SLOGE("Cannot read real block device footer\n");
    goto errout;
  }

  if (crypt_ftr->magic != CRYPT_MNT_MAGIC) {
    SLOGE("Bad magic for real block device %s\n", fname);
    goto errout;
  }

  if (crypt_ftr->major_version != CURRENT_MAJOR_VERSION) {
    SLOGE("Cannot understand major version %d real block device footer; expected %d\n",
          crypt_ftr->major_version, CURRENT_MAJOR_VERSION);
    goto errout;
  }

  if (crypt_ftr->minor_version > CURRENT_MINOR_VERSION) {
    SLOGW("Warning: crypto footer minor version %d, expected <= %d, continuing...\n",
          crypt_ftr->minor_version, CURRENT_MINOR_VERSION);
  }

  /* If this is a verion 1.0 crypt_ftr, make it a 1.1 crypt footer, and update the
   * copy on disk before returning.
   */
  if (crypt_ftr->minor_version < CURRENT_MINOR_VERSION) {
    upgrade_crypt_ftr(fd, crypt_ftr, starting_off);
  }

  /* Success! */
  rc = 0;

errout:
  close(fd);
  return rc;
}

static int validate_persistent_data_storage(struct crypt_mnt_ftr *crypt_ftr)
{
    if (crypt_ftr->persist_data_offset[0] + crypt_ftr->persist_data_size >
        crypt_ftr->persist_data_offset[1]) {
        SLOGE("Crypt_ftr persist data regions overlap");
        return -1;
    }

    if (crypt_ftr->persist_data_offset[0] >= crypt_ftr->persist_data_offset[1]) {
        SLOGE("Crypt_ftr persist data region 0 starts after region 1");
        return -1;
    }

    if (((crypt_ftr->persist_data_offset[1] + crypt_ftr->persist_data_size) -
        (crypt_ftr->persist_data_offset[0] - CRYPT_FOOTER_TO_PERSIST_OFFSET)) >
        CRYPT_FOOTER_OFFSET) {
        SLOGE("Persistent data extends past crypto footer");
        return -1;
    }

    return 0;
}

static int load_persistent_data(void)
{
    struct crypt_mnt_ftr crypt_ftr;
    struct crypt_persist_data *pdata = NULL;
    char encrypted_state[PROPERTY_VALUE_MAX];
    char *fname;
    int found = 0;
    int fd;
    int ret;
    int i;

    if (persist_data) {
        /* Nothing to do, we've already loaded or initialized it */
        return 0;
    }


    /* If not encrypted, just allocate an empty table and initialize it */
    property_get("ro.crypto.state", encrypted_state, "");
    if (strcmp(encrypted_state, "encrypted") ) {
        pdata = malloc(CRYPT_PERSIST_DATA_SIZE);
        if (pdata) {
            init_empty_persist_data(pdata, CRYPT_PERSIST_DATA_SIZE);
            persist_data = pdata;
            return 0;
        }
        return -1;
    }

    if(get_crypt_ftr_and_key(&crypt_ftr)) {
        return -1;
    }

    if ((crypt_ftr.major_version < 1)
        || (crypt_ftr.major_version == 1 && crypt_ftr.minor_version < 1)) {
        SLOGE("Crypt_ftr version doesn't support persistent data");
        return -1;
    }

    if (get_crypt_ftr_info(&fname, NULL)) {
        return -1;
    }

    ret = validate_persistent_data_storage(&crypt_ftr);
    if (ret) {
        return -1;
    }

    fd = open(fname, O_RDONLY|O_CLOEXEC);
    if (fd < 0) {
        SLOGE("Cannot open %s metadata file", fname);
        return -1;
    }

    if (persist_data == NULL) {
        pdata = malloc(crypt_ftr.persist_data_size);
        if (pdata == NULL) {
            SLOGE("Cannot allocate memory for persistent data");
            goto err;
        }
    }

    for (i = 0; i < 2; i++) {
        if (lseek64(fd, crypt_ftr.persist_data_offset[i], SEEK_SET) < 0) {
            SLOGE("Cannot seek to read persistent data on %s", fname);
            goto err2;
        }
        if (unix_read(fd, pdata, crypt_ftr.persist_data_size) < 0){
            SLOGE("Error reading persistent data on iteration %d", i);
            goto err2;
        }
        if (pdata->persist_magic == PERSIST_DATA_MAGIC) {
            found = 1;
            break;
        }
    }

    if (!found) {
        SLOGI("Could not find valid persistent data, creating");
        init_empty_persist_data(pdata, crypt_ftr.persist_data_size);
    }

    /* Success */
    persist_data = pdata;
    close(fd);
    return 0;

err2:
    free(pdata);

err:
    close(fd);
    return -1;
}

static int save_persistent_data(void)
{
    struct crypt_mnt_ftr crypt_ftr;
    struct crypt_persist_data *pdata;
    char *fname;
    off64_t write_offset;
    off64_t erase_offset;
    int fd;
    int ret;

    if (persist_data == NULL) {
        SLOGE("No persistent data to save");
        return -1;
    }

    if(get_crypt_ftr_and_key(&crypt_ftr)) {
        return -1;
    }

    if ((crypt_ftr.major_version < 1)
        || (crypt_ftr.major_version == 1 && crypt_ftr.minor_version < 1)) {
        SLOGE("Crypt_ftr version doesn't support persistent data");
        return -1;
    }

    ret = validate_persistent_data_storage(&crypt_ftr);
    if (ret) {
        return -1;
    }

    if (get_crypt_ftr_info(&fname, NULL)) {
        return -1;
    }

    fd = open(fname, O_RDWR|O_CLOEXEC);
    if (fd < 0) {
        SLOGE("Cannot open %s metadata file", fname);
        return -1;
    }

    pdata = malloc(crypt_ftr.persist_data_size);
    if (pdata == NULL) {
        SLOGE("Cannot allocate persistant data");
        goto err;
    }

    if (lseek64(fd, crypt_ftr.persist_data_offset[0], SEEK_SET) < 0) {
        SLOGE("Cannot seek to read persistent data on %s", fname);
        goto err2;
    }

    if (unix_read(fd, pdata, crypt_ftr.persist_data_size) < 0) {
            SLOGE("Error reading persistent data before save");
            goto err2;
    }

    if (pdata->persist_magic == PERSIST_DATA_MAGIC) {
        /* The first copy is the curent valid copy, so write to
         * the second copy and erase this one */
       write_offset = crypt_ftr.persist_data_offset[1];
       erase_offset = crypt_ftr.persist_data_offset[0];
    } else {
        /* The second copy must be the valid copy, so write to
         * the first copy, and erase the second */
       write_offset = crypt_ftr.persist_data_offset[0];
       erase_offset = crypt_ftr.persist_data_offset[1];
    }

    /* Write the new copy first, if successful, then erase the old copy */
    if (lseek64(fd, write_offset, SEEK_SET) < 0) {
        SLOGE("Cannot seek to write persistent data");
        goto err2;
    }
    if (unix_write(fd, persist_data, crypt_ftr.persist_data_size) ==
        (int) crypt_ftr.persist_data_size) {
        if (lseek64(fd, erase_offset, SEEK_SET) < 0) {
            SLOGE("Cannot seek to erase previous persistent data");
            goto err2;
        }
        fsync(fd);
        memset(pdata, 0, crypt_ftr.persist_data_size);
        if (unix_write(fd, pdata, crypt_ftr.persist_data_size) !=
            (int) crypt_ftr.persist_data_size) {
            SLOGE("Cannot write to erase previous persistent data");
            goto err2;
        }
        fsync(fd);
    } else {
        SLOGE("Cannot write to save persistent data");
        goto err2;
    }

    /* Success */
    free(pdata);
    close(fd);
    return 0;

err2:
    free(pdata);
err:
    close(fd);
    return -1;
}

/* Convert a binary key of specified length into an ascii hex string equivalent,
 * without the leading 0x and with null termination
 */
static void convert_key_to_hex_ascii(const unsigned char *master_key,
                                     unsigned int keysize, char *master_key_ascii) {
    unsigned int i, a;
    unsigned char nibble;

    for (i=0, a=0; i<keysize; i++, a+=2) {
        /* For each byte, write out two ascii hex digits */
        nibble = (master_key[i] >> 4) & 0xf;
        master_key_ascii[a] = nibble + (nibble > 9 ? 0x37 : 0x30);

        nibble = master_key[i] & 0xf;
        master_key_ascii[a+1] = nibble + (nibble > 9 ? 0x37 : 0x30);
    }

    /* Add the null termination */
    master_key_ascii[a] = '\0';

}

static int load_crypto_mapping_table(struct crypt_mnt_ftr *crypt_ftr,
        const unsigned char *master_key, const char *real_blk_name,
        const char *name, int fd, const char *extra_params) {
  _Alignas(struct dm_ioctl) char buffer[DM_CRYPT_BUF_SIZE];
  struct dm_ioctl *io;
  struct dm_target_spec *tgt;
  char *crypt_params;
  char master_key_ascii[129]; /* Large enough to hold 512 bit key and null */
  int i;

  io = (struct dm_ioctl *) buffer;

  /* Load the mapping table for this device */
  tgt = (struct dm_target_spec *) &buffer[sizeof(struct dm_ioctl)];

  ioctl_init(io, DM_CRYPT_BUF_SIZE, name, 0);
  io->target_count = 1;
  tgt->status = 0;
  tgt->sector_start = 0;
  tgt->length = crypt_ftr->fs_size;
#ifdef CONFIG_HW_DISK_ENCRYPTION
  if (!strcmp((char *)crypt_ftr->crypto_type_name, "aes-xts")) {
    strlcpy(tgt->target_type, "req-crypt", DM_MAX_TYPE_NAME);
  }
  else {
    strlcpy(tgt->target_type, "crypt", DM_MAX_TYPE_NAME);
  }
#else
  strlcpy(tgt->target_type, "crypt", DM_MAX_TYPE_NAME);
#endif

  crypt_params = buffer + sizeof(struct dm_ioctl) + sizeof(struct dm_target_spec);
  convert_key_to_hex_ascii(master_key, crypt_ftr->keysize, master_key_ascii);
  sprintf(crypt_params, "%s %s 0 %s 0 %s", crypt_ftr->crypto_type_name,
          master_key_ascii, real_blk_name, extra_params);
  crypt_params += strlen(crypt_params) + 1;
  crypt_params = (char *) (((unsigned long)crypt_params + 7) & ~8); /* Align to an 8 byte boundary */
  tgt->next = crypt_params - buffer;

  for (i = 0; i < TABLE_LOAD_RETRIES; i++) {
    if (! ioctl(fd, DM_TABLE_LOAD, io)) {
      break;
    }
    usleep(500000);
  }

  if (i == TABLE_LOAD_RETRIES) {
    /* We failed to load the table, return an error */
    return -1;
  } else {
    return i + 1;
  }
}


static int get_dm_crypt_version(int fd, const char *name,  int *version)
{
    char buffer[DM_CRYPT_BUF_SIZE];
    struct dm_ioctl *io;
    struct dm_target_versions *v;

    io = (struct dm_ioctl *) buffer;

    ioctl_init(io, DM_CRYPT_BUF_SIZE, name, 0);

    if (ioctl(fd, DM_LIST_VERSIONS, io)) {
        return -1;
    }

    /* Iterate over the returned versions, looking for name of "crypt".
     * When found, get and return the version.
     */
    v = (struct dm_target_versions *) &buffer[sizeof(struct dm_ioctl)];
    while (v->next) {
#ifdef CONFIG_HW_DISK_ENCRYPTION
        if (! strcmp(v->name, "crypt") || ! strcmp(v->name, "req-crypt")) {
#else
        if (! strcmp(v->name, "crypt")) {
#endif
            /* We found the crypt driver, return the version, and get out */
            version[0] = v->version[0];
            version[1] = v->version[1];
            version[2] = v->version[2];
            return 0;
        }
        v = (struct dm_target_versions *)(((char *)v) + v->next);
    }

    return -1;
}

static int create_crypto_blk_dev(struct crypt_mnt_ftr *crypt_ftr,
        const unsigned char *master_key, const char *real_blk_name,
        char *crypto_blk_name, const char *name) {
  char buffer[DM_CRYPT_BUF_SIZE];
  struct dm_ioctl *io;
  unsigned int minor;
  int fd=0;
  int retval = -1;
  int version[3];
  char *extra_params;
  int load_count;

  if ((fd = open("/dev/device-mapper", O_RDWR|O_CLOEXEC)) < 0 ) {
    SLOGE("Cannot open device-mapper\n");
    goto errout;
  }

  io = (struct dm_ioctl *) buffer;

  ioctl_init(io, DM_CRYPT_BUF_SIZE, name, 0);
  if (ioctl(fd, DM_DEV_CREATE, io)) {
    SLOGE("Cannot create dm-crypt device\n");
    goto errout;
  }

  /* Get the device status, in particular, the name of it's device file */
  ioctl_init(io, DM_CRYPT_BUF_SIZE, name, 0);
  if (ioctl(fd, DM_DEV_STATUS, io)) {
    SLOGE("Cannot retrieve dm-crypt device status\n");
    goto errout;
  }
  minor = (io->dev & 0xff) | ((io->dev >> 12) & 0xfff00);
  snprintf(crypto_blk_name, MAXPATHLEN, "/dev/block/dm-%u", minor);

  extra_params = "";
  if (! get_dm_crypt_version(fd, name, version)) {
      /* Support for allow_discards was added in version 1.11.0 */
      if ((version[0] >= 2) ||
          ((version[0] == 1) && (version[1] >= 11))) {
          extra_params = "1 allow_discards";
          SLOGI("Enabling support for allow_discards in dmcrypt.\n");
      }
  }

  load_count = load_crypto_mapping_table(crypt_ftr, master_key, real_blk_name, name,
                                         fd, extra_params);
  if (load_count < 0) {
      SLOGE("Cannot load dm-crypt mapping table.\n");
      goto errout;
  } else if (load_count > 1) {
      SLOGI("Took %d tries to load dmcrypt table.\n", load_count);
  }

  /* Resume this device to activate it */
  ioctl_init(io, DM_CRYPT_BUF_SIZE, name, 0);

  if (ioctl(fd, DM_DEV_SUSPEND, io)) {
    SLOGE("Cannot resume the dm-crypt device\n");
    goto errout;
  }

  /* We made it here with no errors.  Woot! */
  retval = 0;

errout:
  close(fd);   /* If fd is <0 from a failed open call, it's safe to just ignore the close error */

  return retval;
}

static int delete_crypto_blk_dev(char *name)
{
  int fd;
  char buffer[DM_CRYPT_BUF_SIZE];
  struct dm_ioctl *io;
  int retval = -1;

  if ((fd = open("/dev/device-mapper", O_RDWR|O_CLOEXEC)) < 0 ) {
    SLOGE("Cannot open device-mapper\n");
    goto errout;
  }

  io = (struct dm_ioctl *) buffer;

  ioctl_init(io, DM_CRYPT_BUF_SIZE, name, 0);
  if (ioctl(fd, DM_DEV_REMOVE, io)) {
    SLOGE("Cannot remove dm-crypt device\n");
    goto errout;
  }

  /* We made it here with no errors.  Woot! */
  retval = 0;

errout:
  close(fd);    /* If fd is <0 from a failed open call, it's safe to just ignore the close error */

  return retval;

}

static int pbkdf2(const char *passwd, const unsigned char *salt,
                  unsigned char *ikey, void *params UNUSED)
{
    SLOGI("Using pbkdf2 for cryptfs KDF");

    /* Turn the password into a key and IV that can decrypt the master key */
    PKCS5_PBKDF2_HMAC_SHA1(passwd, strlen(passwd),
                           salt, SALT_LEN,
                           HASH_COUNT, KEY_LEN_BYTES+IV_LEN_BYTES, ikey);

    return 0;
}

static int scrypt(const char *passwd, const unsigned char *salt,
                  unsigned char *ikey, void *params)
{
    SLOGI("Using scrypt for cryptfs KDF");

    struct crypt_mnt_ftr *ftr = (struct crypt_mnt_ftr *) params;

    int N = 1 << ftr->N_factor;
    int r = 1 << ftr->r_factor;
    int p = 1 << ftr->p_factor;

    /* Turn the password into a key and IV that can decrypt the master key */
    unsigned int keysize;
    crypto_scrypt((const uint8_t*)passwd, strlen(passwd),
                  salt, SALT_LEN, N, r, p, ikey,
                  KEY_LEN_BYTES + IV_LEN_BYTES);

   return 0;
}

static int scrypt_keymaster(const char *passwd, const unsigned char *salt,
                            unsigned char *ikey, void *params)
{
    SLOGI("Using scrypt with keymaster for cryptfs KDF");

    int rc;
    size_t signature_size;
    unsigned char* signature;
    struct crypt_mnt_ftr *ftr = (struct crypt_mnt_ftr *) params;

    int N = 1 << ftr->N_factor;
    int r = 1 << ftr->r_factor;
    int p = 1 << ftr->p_factor;

    rc = crypto_scrypt((const uint8_t*)passwd, strlen(passwd),
                       salt, SALT_LEN, N, r, p, ikey,
                       KEY_LEN_BYTES + IV_LEN_BYTES);

    if (rc) {
        SLOGE("scrypt failed");
        return -1;
    }

    if (keymaster_sign_object(ftr, ikey, KEY_LEN_BYTES + IV_LEN_BYTES,
                              &signature, &signature_size)) {
        SLOGE("Signing failed");
        return -1;
    }

    rc = crypto_scrypt(signature, signature_size, salt, SALT_LEN,
                       N, r, p, ikey, KEY_LEN_BYTES + IV_LEN_BYTES);
    free(signature);

    if (rc) {
        SLOGE("scrypt failed");
        return -1;
    }

    return 0;
}

static int encrypt_master_key(const char *passwd, const unsigned char *salt,
                              const unsigned char *decrypted_master_key,
                              unsigned char *encrypted_master_key,
                              struct crypt_mnt_ftr *crypt_ftr)
{
    unsigned char ikey[32+32] = { 0 }; /* Big enough to hold a 256 bit key and 256 bit IV */
    EVP_CIPHER_CTX e_ctx;
    int encrypted_len, final_len;
    int rc = 0;

    /* Turn the password into an intermediate key and IV that can decrypt the master key */
    get_device_scrypt_params(crypt_ftr);

    switch (crypt_ftr->kdf_type) {
    case KDF_SCRYPT_KEYMASTER:
        if (keymaster_create_key(crypt_ftr)) {
            SLOGE("keymaster_create_key failed");
            return -1;
        }

        if (scrypt_keymaster(passwd, salt, ikey, crypt_ftr)) {
            SLOGE("scrypt failed");
            return -1;
        }
        break;

    case KDF_SCRYPT:
        if (scrypt(passwd, salt, ikey, crypt_ftr)) {
            SLOGE("scrypt failed");
            return -1;
        }
        break;

    default:
        SLOGE("Invalid kdf_type");
        return -1;
    }

    /* Initialize the decryption engine */
    EVP_CIPHER_CTX_init(&e_ctx);
    if (! EVP_EncryptInit_ex(&e_ctx, EVP_aes_128_cbc(), NULL, ikey, ikey+KEY_LEN_BYTES)) {
        SLOGE("EVP_EncryptInit failed\n");
        return -1;
    }
    EVP_CIPHER_CTX_set_padding(&e_ctx, 0); /* Turn off padding as our data is block aligned */

    /* Encrypt the master key */
    if (! EVP_EncryptUpdate(&e_ctx, encrypted_master_key, &encrypted_len,
                            decrypted_master_key, KEY_LEN_BYTES)) {
        SLOGE("EVP_EncryptUpdate failed\n");
        return -1;
    }
    if (! EVP_EncryptFinal_ex(&e_ctx, encrypted_master_key + encrypted_len, &final_len)) {
        SLOGE("EVP_EncryptFinal failed\n");
        return -1;
    }

    if (encrypted_len + final_len != KEY_LEN_BYTES) {
        SLOGE("EVP_Encryption length check failed with %d, %d bytes\n", encrypted_len, final_len);
        return -1;
    }

    /* Store the scrypt of the intermediate key, so we can validate if it's a
       password error or mount error when things go wrong.
       Note there's no need to check for errors, since if this is incorrect, we
       simply won't wipe userdata, which is the correct default behavior
    */
    int N = 1 << crypt_ftr->N_factor;
    int r = 1 << crypt_ftr->r_factor;
    int p = 1 << crypt_ftr->p_factor;

    rc = crypto_scrypt(ikey, KEY_LEN_BYTES,
                       crypt_ftr->salt, sizeof(crypt_ftr->salt), N, r, p,
                       crypt_ftr->scrypted_intermediate_key,
                       sizeof(crypt_ftr->scrypted_intermediate_key));

    if (rc) {
      SLOGE("encrypt_master_key: crypto_scrypt failed");
    }

    return 0;
}

static int decrypt_master_key_aux(const char *passwd, unsigned char *salt,
                                  unsigned char *encrypted_master_key,
                                  unsigned char *decrypted_master_key,
                                  kdf_func kdf, void *kdf_params,
                                  unsigned char** intermediate_key,
                                  size_t* intermediate_key_size)
{
  unsigned char ikey[32+32] = { 0 }; /* Big enough to hold a 256 bit key and 256 bit IV */
  EVP_CIPHER_CTX d_ctx;
  int decrypted_len, final_len;

  /* Turn the password into an intermediate key and IV that can decrypt the
     master key */
  if (kdf(passwd, salt, ikey, kdf_params)) {
    SLOGE("kdf failed");
    return -1;
  }

  /* Initialize the decryption engine */
  EVP_CIPHER_CTX_init(&d_ctx);
  if (! EVP_DecryptInit_ex(&d_ctx, EVP_aes_128_cbc(), NULL, ikey, ikey+KEY_LEN_BYTES)) {
    return -1;
  }
  EVP_CIPHER_CTX_set_padding(&d_ctx, 0); /* Turn off padding as our data is block aligned */
  /* Decrypt the master key */
  if (! EVP_DecryptUpdate(&d_ctx, decrypted_master_key, &decrypted_len,
                            encrypted_master_key, KEY_LEN_BYTES)) {
    return -1;
  }
  if (! EVP_DecryptFinal_ex(&d_ctx, decrypted_master_key + decrypted_len, &final_len)) {
    return -1;
  }

  if (decrypted_len + final_len != KEY_LEN_BYTES) {
    return -1;
  }

  /* Copy intermediate key if needed by params */
  if (intermediate_key && intermediate_key_size) {
    *intermediate_key = (unsigned char*) malloc(KEY_LEN_BYTES);
    if (intermediate_key) {
      memcpy(*intermediate_key, ikey, KEY_LEN_BYTES);
      *intermediate_key_size = KEY_LEN_BYTES;
    }
  }

  return 0;
}

static void get_kdf_func(struct crypt_mnt_ftr *ftr, kdf_func *kdf, void** kdf_params)
{
    if (ftr->kdf_type == KDF_SCRYPT_KEYMASTER) {
        *kdf = scrypt_keymaster;
        *kdf_params = ftr;
    } else if (ftr->kdf_type == KDF_SCRYPT) {
        *kdf = scrypt;
        *kdf_params = ftr;
    } else {
        *kdf = pbkdf2;
        *kdf_params = NULL;
    }
}

static int decrypt_master_key(const char *passwd, unsigned char *decrypted_master_key,
                              struct crypt_mnt_ftr *crypt_ftr,
                              unsigned char** intermediate_key,
                              size_t* intermediate_key_size)
{
    kdf_func kdf;
    void *kdf_params;
    int ret;

    get_kdf_func(crypt_ftr, &kdf, &kdf_params);
    ret = decrypt_master_key_aux(passwd, crypt_ftr->salt, crypt_ftr->master_key,
                                 decrypted_master_key, kdf, kdf_params,
                                 intermediate_key, intermediate_key_size);
    if (ret != 0) {
        SLOGW("failure decrypting master key");
    }

    return ret;
}

static int create_encrypted_random_key(char *passwd, unsigned char *master_key, unsigned char *salt,
        struct crypt_mnt_ftr *crypt_ftr) {
    int fd;
    unsigned char key_buf[KEY_LEN_BYTES];

    /* Get some random bits for a key */
    fd = open("/dev/urandom", O_RDONLY|O_CLOEXEC);
    read(fd, key_buf, sizeof(key_buf));
    read(fd, salt, SALT_LEN);
    close(fd);

    /* Now encrypt it with the password */
    return encrypt_master_key(passwd, salt, key_buf, master_key, crypt_ftr);
}

/* SPRD: Add for boot performance in cryptfs mode {@ */
static int get_pid_by_name(const char *in_name){
    DIR* dir;
    struct dirent* de;
    int pid = -1;
    int rt_pid = -1;
    char name[PATH_MAX];

    if (!(dir = opendir("/proc"))){
        SLOGE("opendir failed (%s)", strerror(errno));
        return -1;
    }
    while ((de = readdir(dir))) {
        pid = vold_getPid(de->d_name);
        if (pid == -1) {
            continue;
        }
        vold_getProcessName(pid, name, sizeof(name));
        if (!strcmp(name, in_name)) {
            rt_pid = pid;
            break;
        }
    }
    closedir(dir);
    SLOGD("%s(): leave  pid = %d", __FUNCTION__, rt_pid);
    return rt_pid;
}

static int get_open_process(const char *path){
    DIR* dir;
    struct dirent* de;
    int rc = 0;
    int need_kill_pid = -1;

    need_kill_pid = get_pid_by_name("/system/bin/sprd_res_monitor");

    if (!(dir = opendir("/proc"))){
        SLOGE("opendir failed (%s)", strerror(errno));
        return -1;
    }

    while ((de = readdir(dir))) {
        int pid = vold_getPid(de->d_name);
        char name[PATH_MAX];

        if (pid == -1)
            continue;
        vold_getProcessName(pid, name, sizeof(name));
        char openfile[PATH_MAX];

        if (vold_checkFileDescriptorSymLinks(pid, path, openfile, sizeof(openfile))) {
//        if (vold_checkAll(pid, name, path, openfile)) {

            SLOGE("Process %s (%d) has open file %s", name, pid, openfile);
            if (!strcmp(name, "logcat")) {
                SLOGD("will kill %s (%d)", name, pid);
                kill(pid, SIGKILL);
                rc = 1;
                break;
            }
            if (!strcmp(name, "/system/bin/modemDriver_vpad_main")) {
                SLOGD("will kill %s (%d)", name, pid);
                kill(pid, SIGKILL);
                rc = 1;
                break;
            }
            if (!strcmp(name, "/system/bin/slog")) {
                // Need kill sprd_res_monitor 1st
                SLOGD("need_kill_pid = %d", need_kill_pid);
                if (need_kill_pid != -1) {
                    SLOGD("1st kill (%d)", need_kill_pid);
                    kill(need_kill_pid, SIGKILL);
                    need_kill_pid = -1;
                }
                SLOGD("will kill %s (%d)", name, pid);
                kill(pid, SIGKILL);
                rc = 1;
                break;
            }
        } else if (vold_checkSymLink(pid, path, "cwd")) {
            SLOGE("Process %s (%d) has cwd within path %s", name, pid, path);
            if (!strcmp(name, "/system/bin/gatekeeperd")) {
                SLOGD("will kill process %s (%d)", name, pid);
                kill(pid, SIGKILL);
                rc = 1;
                break;
            }
        }
    }
    closedir(dir);

    return rc;
}
/* @} */

int wait_and_unmount(const char *mountpoint, bool kill)
{
    int i, err, rc;
#define WAIT_UNMOUNT_COUNT 20

    /*  Now umount the tmpfs filesystem */
    for (i=0; i<WAIT_UNMOUNT_COUNT; i++) {
        if (strcmp(mountpoint, DATA_MNT_POINT) == 0) {
	            umount("/mnt/runtime/default/emulated");
	            umount("/mnt/runtime/read/emulated");
	            umount("/mnt/runtime/write/emulated");
        }
        if (umount(mountpoint) == 0) {
            break;
        }

        if (errno == EINVAL) {
            /* EINVAL is returned if the directory is not a mountpoint,
             * i.e. there is no filesystem mounted there.  So just get out.
             */
            break;
        }

        err = errno;

        /* If allowed, be increasingly aggressive before the last two retries */
        if (kill) {
            /* SPRD: Add for boot performance in cryptfs mode {@ */
            if (get_open_process(mountpoint))
                continue;
            /* @} */
            if (i == (WAIT_UNMOUNT_COUNT - 3)) {
                SLOGW("sending SIGHUP to processes with open files\n");
                vold_killProcessesWithOpenFiles(mountpoint, SIGTERM);
            } else if (i == (WAIT_UNMOUNT_COUNT - 2)) {
                SLOGW("sending SIGKILL to processes with open files\n");
                vold_killProcessesWithOpenFiles(mountpoint, SIGKILL);
            }
        }

        sleep(1);
    }

    if (i < WAIT_UNMOUNT_COUNT) {
      SLOGD("unmounting %s succeeded\n", mountpoint);
      rc = 0;
    } else {
      vold_killProcessesWithOpenFiles(mountpoint, 0);
      SLOGE("unmounting %s failed: %s\n", mountpoint, strerror(err));
      rc = -1;
    }

    return rc;
}

#define DATA_PREP_TIMEOUT 1000
static int prep_data_fs(void)
{
    int i;

    /* Do the prep of the /data filesystem */
    property_set("vold.post_fs_data_done", "0");
    property_set("vold.decrypt", "trigger_post_fs_data");
    SLOGD("Just triggered post_fs_data\n");

    /* Wait a max of 50 seconds, hopefully it takes much less */
    for (i=0; i<DATA_PREP_TIMEOUT; i++) {
        char p[PROPERTY_VALUE_MAX];

        property_get("vold.post_fs_data_done", p, "0");
        if (*p == '1') {
            break;
        } else {
            usleep(50000);
        }
    }
    if (i == DATA_PREP_TIMEOUT) {
        /* Ugh, we failed to prep /data in time.  Bail. */
        SLOGE("post_fs_data timed out!\n");
        return -1;
    } else {
        SLOGD("post_fs_data done\n");
        return 0;
    }
}

static void cryptfs_set_corrupt()
{
    // Mark the footer as bad
    struct crypt_mnt_ftr crypt_ftr;
    if (get_crypt_ftr_and_key(&crypt_ftr)) {
        SLOGE("Failed to get crypto footer - panic");
        return;
    }

    crypt_ftr.flags |= CRYPT_DATA_CORRUPT;
    if (put_crypt_ftr_and_key(&crypt_ftr)) {
        SLOGE("Failed to set crypto footer - panic");
        return;
    }
}

static void cryptfs_trigger_restart_min_framework()
{
    if (fs_mgr_do_tmpfs_mount(DATA_MNT_POINT)) {
      SLOGE("Failed to mount tmpfs on data - panic");
      return;
    }

    if (property_set("vold.decrypt", "trigger_post_fs_data")) {
        SLOGE("Failed to trigger post fs data - panic");
        return;
    }

    if (property_set("vold.decrypt", "trigger_restart_min_framework")) {
        SLOGE("Failed to trigger restart min framework - panic");
        return;
    }
}

/* returns < 0 on failure */
static int cryptfs_restart_internal(int restart_main)
{
    char crypto_blkdev[MAXPATHLEN];
    int rc = -1;
    static int restart_successful = 0;

    /* Validate that it's OK to call this routine */
    if (! master_key_saved) {
        SLOGE("Encrypted filesystem not validated, aborting");
        return -1;
    }

    if (restart_successful) {
        SLOGE("System already restarted with encrypted disk, aborting");
        return -1;
    }

    if (restart_main) {
        /* Here is where we shut down the framework.  The init scripts
         * start all services in one of three classes: core, main or late_start.
         * On boot, we start core and main.  Now, we stop main, but not core,
         * as core includes vold and a few other really important things that
         * we need to keep running.  Once main has stopped, we should be able
         * to umount the tmpfs /data, then mount the encrypted /data.
         * We then restart the class main, and also the class late_start.
         * At the moment, I've only put a few things in late_start that I know
         * are not needed to bring up the framework, and that also cause problems
         * with unmounting the tmpfs /data, but I hope to add add more services
         * to the late_start class as we optimize this to decrease the delay
         * till the user is asked for the password to the filesystem.
         */

        /* The init files are setup to stop the class main when vold.decrypt is
         * set to trigger_reset_main.
         */
        property_set("vold.decrypt", "trigger_reset_main");
        SLOGD("Just asked init to shut down class main\n");

        /* Ugh, shutting down the framework is not synchronous, so until it
         * can be fixed, this horrible hack will wait a moment for it all to
         * shut down before proceeding.  Without it, some devices cannot
         * restart the graphics services.
         */
        sleep(2);
    }

    /* Now that the framework is shutdown, we should be able to umount()
     * the tmpfs filesystem, and mount the real one.
     */

    property_get("ro.crypto.fs_crypto_blkdev", crypto_blkdev, "");
    if (strlen(crypto_blkdev) == 0) {
        SLOGE("fs_crypto_blkdev not set\n");
        return -1;
    }

    if (! (rc = wait_and_unmount(DATA_MNT_POINT, true)) ) {
        /* If ro.crypto.readonly is set to 1, mount the decrypted
         * filesystem readonly.  This is used when /data is mounted by
         * recovery mode.
         */
        char ro_prop[PROPERTY_VALUE_MAX];
        property_get("ro.crypto.readonly", ro_prop, "");
        if (strlen(ro_prop) > 0 && atoi(ro_prop)) {
            struct fstab_rec* rec = fs_mgr_get_entry_for_mount_point(fstab, DATA_MNT_POINT);
            rec->flags |= MS_RDONLY;
        }

        /* If that succeeded, then mount the decrypted filesystem */
        int retries = RETRY_MOUNT_ATTEMPTS;
        int mount_rc;
        while ((mount_rc = fs_mgr_do_mount(fstab, DATA_MNT_POINT,
                                           crypto_blkdev, 0))
               != 0) {
            if (mount_rc == FS_MGR_DOMNT_BUSY) {
                /* TODO: invoke something similar to
                   Process::killProcessWithOpenFiles(DATA_MNT_POINT,
                                   retries > RETRY_MOUNT_ATTEMPT/2 ? 1 : 2 ) */
                SLOGI("Failed to mount %s because it is busy - waiting",
                      crypto_blkdev);
                if (--retries) {
                    sleep(RETRY_MOUNT_DELAY_SECONDS);
                } else {
                    /* Let's hope that a reboot clears away whatever is keeping
                       the mount busy */
                    cryptfs_reboot(reboot);
                }
            } else {
                SLOGE("Failed to mount decrypted data");
                cryptfs_set_corrupt();
                cryptfs_trigger_restart_min_framework();
                SLOGI("Started framework to offer wipe");
                return -1;
            }
        }

        property_set("vold.decrypt", "trigger_load_persist_props");
        /* Create necessary paths on /data */
        if (prep_data_fs()) {
            return -1;
        }

        /* startup service classes main and late_start */
        property_set("vold.decrypt", "trigger_restart_framework");
        SLOGD("Just triggered restart_framework\n");

        /* Give it a few moments to get started */
        sleep(1);
    }

    if (rc == 0) {
        restart_successful = 1;
    }

    return rc;
}

int cryptfs_restart(void)
{
    SLOGI("cryptfs_restart");
    if (e4crypt_crypto_complete(DATA_MNT_POINT) == 0) {
        struct fstab_rec* rec;
        int rc;

        if (e4crypt_restart(DATA_MNT_POINT)) {
            SLOGE("Can't unmount e4crypt temp volume\n");
            return -1;
        }

        rec = fs_mgr_get_entry_for_mount_point(fstab, DATA_MNT_POINT);
        if (!rec) {
            SLOGE("Can't get fstab record for %s\n", DATA_MNT_POINT);
            return -1;
        }

        rc = fs_mgr_do_mount(fstab, DATA_MNT_POINT, rec->blk_device, 0);
        if (rc) {
            SLOGE("Can't mount %s\n", DATA_MNT_POINT);
            return rc;
        }

        property_set("vold.decrypt", "trigger_restart_framework");
        return 0;
    }

    /* Call internal implementation forcing a restart of main service group */
    return cryptfs_restart_internal(1);
}

static int do_crypto_complete(char *mount_point)
{
  struct crypt_mnt_ftr crypt_ftr;
  char encrypted_state[PROPERTY_VALUE_MAX];
  char key_loc[PROPERTY_VALUE_MAX];

  property_get("ro.crypto.state", encrypted_state, "");
  if (strcmp(encrypted_state, "encrypted") ) {
    SLOGE("not running with encryption, aborting");
    return CRYPTO_COMPLETE_NOT_ENCRYPTED;
  }

  if (e4crypt_crypto_complete(mount_point) == 0) {
    return CRYPTO_COMPLETE_ENCRYPTED;
  }

  if (get_crypt_ftr_and_key(&crypt_ftr)) {
    fs_mgr_get_crypt_info(fstab, key_loc, 0, sizeof(key_loc));

    /*
     * Only report this error if key_loc is a file and it exists.
     * If the device was never encrypted, and /data is not mountable for
     * some reason, returning 1 should prevent the UI from presenting the
     * a "enter password" screen, or worse, a "press button to wipe the
     * device" screen.
     */
    if ((key_loc[0] == '/') && (access("key_loc", F_OK) == -1)) {
      SLOGE("master key file does not exist, aborting");
      return CRYPTO_COMPLETE_NOT_ENCRYPTED;
    } else {
      SLOGE("Error getting crypt footer and key\n");
      return CRYPTO_COMPLETE_BAD_METADATA;
    }
  }

  // Test for possible error flags
  if (crypt_ftr.flags & CRYPT_ENCRYPTION_IN_PROGRESS){
    SLOGE("Encryption process is partway completed\n");
    return CRYPTO_COMPLETE_PARTIAL;
  }

  if (crypt_ftr.flags & CRYPT_INCONSISTENT_STATE){
    SLOGE("Encryption process was interrupted but cannot continue\n");
    return CRYPTO_COMPLETE_INCONSISTENT;
  }

  if (crypt_ftr.flags & CRYPT_DATA_CORRUPT){
    SLOGE("Encryption is successful but data is corrupt\n");
    return CRYPTO_COMPLETE_CORRUPT;
  }

  /* We passed the test! We shall diminish, and return to the west */
  return CRYPTO_COMPLETE_ENCRYPTED;
}

static int test_mount_encrypted_fs(struct crypt_mnt_ftr* crypt_ftr,
                                   char *passwd, char *mount_point, char *label)
{
  /* Allocate enough space for a 256 bit key, but we may use less */
  unsigned char decrypted_master_key[32];
  char crypto_blkdev[MAXPATHLEN];
  char real_blkdev[MAXPATHLEN];
  char tmp_mount_point[64];
  unsigned int orig_failed_decrypt_count;
  int rc;
  int use_keymaster = 0;
  int upgrade = 0;
  unsigned char* intermediate_key = 0;
  size_t intermediate_key_size = 0;

  SLOGD("crypt_ftr->fs_size = %lld\n", crypt_ftr->fs_size);
  orig_failed_decrypt_count = crypt_ftr->failed_decrypt_count;

  if (! (crypt_ftr->flags & CRYPT_MNT_KEY_UNENCRYPTED) ) {
    if (decrypt_master_key(passwd, decrypted_master_key, crypt_ftr,
                           &intermediate_key, &intermediate_key_size)) {
      SLOGE("Failed to decrypt master key\n");
      rc = -1;
      goto errout;
    }
  }

  fs_mgr_get_crypt_info(fstab, 0, real_blkdev, sizeof(real_blkdev));

#ifdef CONFIG_HW_DISK_ENCRYPTION
  if (!strcmp((char *)crypt_ftr->crypto_type_name, "aes-xts")) {
    if(!set_hw_device_encryption_key(passwd, (char*) crypt_ftr->crypto_type_name)) {
      SLOGE("Hardware encryption key does not match");
    }
  }
#endif

  // Create crypto block device - all (non fatal) code paths
  // need it
  if (create_crypto_blk_dev(crypt_ftr, decrypted_master_key,
                            real_blkdev, crypto_blkdev, label)) {
     SLOGE("Error creating decrypted block device\n");
     rc = -1;
     goto errout;
  }

  /* Work out if the problem is the password or the data */
  unsigned char scrypted_intermediate_key[sizeof(crypt_ftr->
                                                 scrypted_intermediate_key)];
  int N = 1 << crypt_ftr->N_factor;
  int r = 1 << crypt_ftr->r_factor;
  int p = 1 << crypt_ftr->p_factor;

  rc = crypto_scrypt(intermediate_key, intermediate_key_size,
                     crypt_ftr->salt, sizeof(crypt_ftr->salt),
                     N, r, p, scrypted_intermediate_key,
                     sizeof(scrypted_intermediate_key));

  // Does the key match the crypto footer?
  if (rc == 0 && memcmp(scrypted_intermediate_key,
                        crypt_ftr->scrypted_intermediate_key,
                        sizeof(scrypted_intermediate_key)) == 0) {
    SLOGI("Password matches");
    rc = 0;
  } else {
    /* Try mounting the file system anyway, just in case the problem's with
     * the footer, not the key. */
    sprintf(tmp_mount_point, "%s/tmp_mnt", mount_point);
    mkdir(tmp_mount_point, 0755);
    if (fs_mgr_do_mount(fstab, DATA_MNT_POINT, crypto_blkdev, tmp_mount_point)) {
      SLOGE("Error temp mounting decrypted block device\n");
      delete_crypto_blk_dev(label);

      rc = ++crypt_ftr->failed_decrypt_count;
      put_crypt_ftr_and_key(crypt_ftr);
    } else {
      /* Success! */
      SLOGI("Password did not match but decrypted drive mounted - continue");
      umount(tmp_mount_point);
      rc = 0;
    }
  }

  if (rc == 0) {
    crypt_ftr->failed_decrypt_count = 0;
    if (orig_failed_decrypt_count != 0) {
      put_crypt_ftr_and_key(crypt_ftr);
    }

    /* Save the name of the crypto block device
     * so we can mount it when restarting the framework. */
    property_set("ro.crypto.fs_crypto_blkdev", crypto_blkdev);

    /* Also save a the master key so we can reencrypted the key
     * the key when we want to change the password on it. */
    memcpy(saved_master_key, decrypted_master_key, KEY_LEN_BYTES);
    saved_mount_point = strdup(mount_point);
    master_key_saved = 1;
    SLOGD("%s(): Master key saved\n", __FUNCTION__);
    rc = 0;

    // Upgrade if we're not using the latest KDF.
    use_keymaster = keymaster_check_compatibility();
    if (crypt_ftr->kdf_type == KDF_SCRYPT_KEYMASTER) {
        // Don't allow downgrade
    } else if (use_keymaster == 1 && crypt_ftr->kdf_type != KDF_SCRYPT_KEYMASTER) {
        crypt_ftr->kdf_type = KDF_SCRYPT_KEYMASTER;
        upgrade = 1;
    } else if (use_keymaster == 0 && crypt_ftr->kdf_type != KDF_SCRYPT) {
        crypt_ftr->kdf_type = KDF_SCRYPT;
        upgrade = 1;
    }

    if (upgrade) {
        rc = encrypt_master_key(passwd, crypt_ftr->salt, saved_master_key,
                                crypt_ftr->master_key, crypt_ftr);
        if (!rc) {
            rc = put_crypt_ftr_and_key(crypt_ftr);
        }
        SLOGD("Key Derivation Function upgrade: rc=%d\n", rc);

        // Do not fail even if upgrade failed - machine is bootable
        // Note that if this code is ever hit, there is a *serious* problem
        // since KDFs should never fail. You *must* fix the kdf before
        // proceeding!
        if (rc) {
          SLOGW("Upgrade failed with error %d,"
                " but continuing with previous state",
                rc);
          rc = 0;
        }
    }
  }

 errout:
  if (intermediate_key) {
    memset(intermediate_key, 0, intermediate_key_size);
    free(intermediate_key);
  }
  return rc;
}

/*
 * Called by vold when it's asked to mount an encrypted external
 * storage volume. The incoming partition has no crypto header/footer,
 * as any metadata is been stored in a separate, small partition.
 *
 * out_crypto_blkdev must be MAXPATHLEN.
 */
int cryptfs_setup_ext_volume(const char* label, const char* real_blkdev,
        const unsigned char* key, int keysize, char* out_crypto_blkdev) {
    int fd = open(real_blkdev, O_RDONLY|O_CLOEXEC);
    if (fd == -1) {
        SLOGE("Failed to open %s: %s", real_blkdev, strerror(errno));
        return -1;
    }

    unsigned long nr_sec = 0;
    get_blkdev_size(fd, &nr_sec);
    close(fd);

    if (nr_sec == 0) {
        SLOGE("Failed to get size of %s: %s", real_blkdev, strerror(errno));
        return -1;
    }

    struct crypt_mnt_ftr ext_crypt_ftr;
    memset(&ext_crypt_ftr, 0, sizeof(ext_crypt_ftr));
    ext_crypt_ftr.fs_size = nr_sec;
    ext_crypt_ftr.keysize = keysize;
    strcpy((char*) ext_crypt_ftr.crypto_type_name, "aes-cbc-essiv:sha256");

    return create_crypto_blk_dev(&ext_crypt_ftr, key, real_blkdev,
            out_crypto_blkdev, label);
}

/*
 * Called by vold when it's asked to unmount an encrypted external
 * storage volume.
 */
int cryptfs_revert_ext_volume(const char* label) {
    return delete_crypto_blk_dev((char*) label);
}

int cryptfs_crypto_complete(void)
{
  return do_crypto_complete("/data");
}

int check_unmounted_and_get_ftr(struct crypt_mnt_ftr* crypt_ftr)
{
    char encrypted_state[PROPERTY_VALUE_MAX];
    property_get("ro.crypto.state", encrypted_state, "");
    if ( master_key_saved || strcmp(encrypted_state, "encrypted") ) {
        SLOGE("encrypted fs already validated or not running with encryption,"
              " aborting");
        return -1;
    }

    if (get_crypt_ftr_and_key(crypt_ftr)) {
        SLOGE("Error getting crypt footer and key");
        return -1;
    }

    return 0;
}

int cryptfs_check_passwd(char *passwd)
{
    SLOGI("cryptfs_check_passwd");
    if (e4crypt_crypto_complete(DATA_MNT_POINT) == 0) {
        return e4crypt_check_passwd(DATA_MNT_POINT, passwd);
    }

    struct crypt_mnt_ftr crypt_ftr;
    int rc;

    rc = check_unmounted_and_get_ftr(&crypt_ftr);
    if (rc)
        return rc;

    rc = test_mount_encrypted_fs(&crypt_ftr, passwd,
                                 DATA_MNT_POINT, "userdata");

    if (rc == 0 && crypt_ftr.crypt_type != CRYPT_TYPE_DEFAULT) {
        cryptfs_clear_password();
        password = strdup(passwd);
        struct timespec now;
        clock_gettime(CLOCK_BOOTTIME, &now);
        password_expiry_time = now.tv_sec + password_max_age_seconds;
    }

    return rc;
}

int cryptfs_verify_passwd(char *passwd)
{
    struct crypt_mnt_ftr crypt_ftr;
    /* Allocate enough space for a 256 bit key, but we may use less */
    unsigned char decrypted_master_key[32];
    char encrypted_state[PROPERTY_VALUE_MAX];
    int rc;

    property_get("ro.crypto.state", encrypted_state, "");
    if (strcmp(encrypted_state, "encrypted") ) {
        SLOGE("device not encrypted, aborting");
        return -2;
    }

    if (!master_key_saved) {
        SLOGE("encrypted fs not yet mounted, aborting");
        return -1;
    }

    if (!saved_mount_point) {
        SLOGE("encrypted fs failed to save mount point, aborting");
        return -1;
    }

    if (get_crypt_ftr_and_key(&crypt_ftr)) {
        SLOGE("Error getting crypt footer and key\n");
        return -1;
    }

    if (crypt_ftr.flags & CRYPT_MNT_KEY_UNENCRYPTED) {
        /* If the device has no password, then just say the password is valid */
        rc = 0;
    } else {
        decrypt_master_key(passwd, decrypted_master_key, &crypt_ftr, 0, 0);
        if (!memcmp(decrypted_master_key, saved_master_key, crypt_ftr.keysize)) {
            /* They match, the password is correct */
            rc = 0;
        } else {
            /* If incorrect, sleep for a bit to prevent dictionary attacks */
            sleep(1);
            rc = 1;
        }
    }

    return rc;
}

/* Initialize a crypt_mnt_ftr structure.  The keysize is
 * defaulted to 16 bytes, and the filesystem size to 0.
 * Presumably, at a minimum, the caller will update the
 * filesystem size and crypto_type_name after calling this function.
 */
static int cryptfs_init_crypt_mnt_ftr(struct crypt_mnt_ftr *ftr)
{
    off64_t off;

    memset(ftr, 0, sizeof(struct crypt_mnt_ftr));
    ftr->magic = CRYPT_MNT_MAGIC;
    ftr->major_version = CURRENT_MAJOR_VERSION;
    ftr->minor_version = CURRENT_MINOR_VERSION;
    ftr->ftr_size = sizeof(struct crypt_mnt_ftr);
    ftr->keysize = KEY_LEN_BYTES;

    switch (keymaster_check_compatibility()) {
    case 1:
        ftr->kdf_type = KDF_SCRYPT_KEYMASTER;
        break;

    case 0:
        ftr->kdf_type = KDF_SCRYPT;
        break;

    default:
        SLOGE("keymaster_check_compatibility failed");
        return -1;
    }

    get_device_scrypt_params(ftr);

    ftr->persist_data_size = CRYPT_PERSIST_DATA_SIZE;
    if (get_crypt_ftr_info(NULL, &off) == 0) {
        ftr->persist_data_offset[0] = off + CRYPT_FOOTER_TO_PERSIST_OFFSET;
        ftr->persist_data_offset[1] = off + CRYPT_FOOTER_TO_PERSIST_OFFSET +
                                    ftr->persist_data_size;
    }

    return 0;
}

static int cryptfs_enable_wipe(char *crypto_blkdev, off64_t size, int type)
{
    const char *args[10];
    char size_str[32]; /* Must be large enough to hold a %lld and null byte */
    int num_args;
    int status;
    int tmp;
    int rc = -1;

    if (type == EXT4_FS) {
        args[0] = "/system/bin/make_ext4fs";
        args[1] = "-a";
        args[2] = "/data";
        args[3] = "-l";
        snprintf(size_str, sizeof(size_str), "%" PRId64, size * 512);
        args[4] = size_str;
        args[5] = crypto_blkdev;
        num_args = 6;
        SLOGI("Making empty filesystem with command %s %s %s %s %s %s\n",
              args[0], args[1], args[2], args[3], args[4], args[5]);
    } else if (type == F2FS_FS) {
        args[0] = "/system/bin/mkfs.f2fs";
        args[1] = "-t";
        args[2] = "-d1";
        args[3] = crypto_blkdev;
        snprintf(size_str, sizeof(size_str), "%" PRId64, size);
        args[4] = size_str;
        num_args = 5;
        SLOGI("Making empty filesystem with command %s %s %s %s %s\n",
              args[0], args[1], args[2], args[3], args[4]);
    } else {
        SLOGE("cryptfs_enable_wipe(): unknown filesystem type %d\n", type);
        return -1;
    }

    tmp = android_fork_execvp(num_args, (char **)args, &status, false, true);

    if (tmp != 0) {
      SLOGE("Error creating empty filesystem on %s due to logwrap error\n", crypto_blkdev);
    } else {
        if (WIFEXITED(status)) {
            if (WEXITSTATUS(status)) {
                SLOGE("Error creating filesystem on %s, exit status %d ",
                      crypto_blkdev, WEXITSTATUS(status));
            } else {
                SLOGD("Successfully created filesystem on %s\n", crypto_blkdev);
                rc = 0;
            }
        } else {
            SLOGE("Error creating filesystem on %s, did not exit normally\n", crypto_blkdev);
       }
    }

    return rc;
}

#define CRYPT_INPLACE_BUFSIZE 4096
#define CRYPT_SECTORS_PER_BUFSIZE (CRYPT_INPLACE_BUFSIZE / CRYPT_SECTOR_SIZE)
#define CRYPT_SECTOR_SIZE 512

/* aligned 32K writes tends to make flash happy.
 * SD card association recommends it.
 */
#ifndef CONFIG_HW_DISK_ENCRYPTION
#define BLOCKS_AT_A_TIME 8
#else
#define BLOCKS_AT_A_TIME 1024
#endif

struct encryptGroupsData
{
    int realfd;
    int cryptofd;
    off64_t numblocks;
    off64_t one_pct, cur_pct, new_pct;
    off64_t blocks_already_done, tot_numblocks;
    off64_t used_blocks_already_done, tot_used_blocks;
    char* real_blkdev, * crypto_blkdev;
    int count;
    off64_t offset;
    char* buffer;
    off64_t last_written_sector;
    int completed;
    time_t time_started;
    int remaining_time;
};

static void update_progress(struct encryptGroupsData* data, int is_used)
{
    data->blocks_already_done++;

    if (is_used) {
        data->used_blocks_already_done++;
    }
    if (data->tot_used_blocks) {
        data->new_pct = data->used_blocks_already_done / data->one_pct;
    } else {
        data->new_pct = data->blocks_already_done / data->one_pct;
    }

    if (data->new_pct > data->cur_pct) {
        char buf[8];
        data->cur_pct = data->new_pct;
        snprintf(buf, sizeof(buf), "%" PRId64, data->cur_pct);
        property_set("vold.encrypt_progress", buf);
    }

    if (data->cur_pct >= 5) {
        struct timespec time_now;
        if (clock_gettime(CLOCK_MONOTONIC, &time_now)) {
            SLOGW("Error getting time");
        } else {
            double elapsed_time = difftime(time_now.tv_sec, data->time_started);
            off64_t remaining_blocks = data->tot_used_blocks
                                       - data->used_blocks_already_done;
            int remaining_time = (int)(elapsed_time * remaining_blocks
                                       / data->used_blocks_already_done);

            // Change time only if not yet set, lower, or a lot higher for
            // best user experience
            if (data->remaining_time == -1
                || remaining_time < data->remaining_time
                || remaining_time > data->remaining_time + 60) {
                char buf[8];
                snprintf(buf, sizeof(buf), "%d", remaining_time);
                property_set("vold.encrypt_time_remaining", buf);
                data->remaining_time = remaining_time;
            }
        }
    }
}

static void log_progress(struct encryptGroupsData const* data, bool completed)
{
    // Precondition - if completed data = 0 else data != 0

    // Track progress so we can skip logging blocks
    static off64_t offset = -1;

    // Need to close existing 'Encrypting from' log?
    if (completed || (offset != -1 && data->offset != offset)) {
        SLOGI("Encrypted to sector %" PRId64,
              offset / info.block_size * CRYPT_SECTOR_SIZE);
        offset = -1;
    }

    // Need to start new 'Encrypting from' log?
    if (!completed && offset != data->offset) {
        SLOGI("Encrypting from sector %" PRId64,
              data->offset / info.block_size * CRYPT_SECTOR_SIZE);
    }

    // Update offset
    if (!completed) {
        offset = data->offset + (off64_t)data->count * info.block_size;
    }
}

static int flush_outstanding_data(struct encryptGroupsData* data)
{
    if (data->count == 0) {
        return 0;
    }

    SLOGV("Copying %d blocks at offset %" PRIx64, data->count, data->offset);

    if (pread64(data->realfd, data->buffer,
                info.block_size * data->count, data->offset)
        <= 0) {
        SLOGE("Error reading real_blkdev %s for inplace encrypt",
              data->real_blkdev);
        return -1;
    }

    if (0 == data->offset) {
        if (pwrite64(data->cryptofd, data->buffer, info.block_size * 1, data->offset)
            <= 0) {
            SLOGE("Error writing crypto_blkdev %s for inplace encrypt",
                  data->crypto_blkdev);
            return -1;
        }
        fsync(data->cryptofd);
        ioctl(data->cryptofd, BLKFLSBUF, 0);
        SLOGI("Encrypting superblock sector %" PRId64, data->offset);
	if (data->count > 1) {
            if (pwrite64(data->cryptofd, data->buffer + info.block_size, info.block_size * (data->count - 1), data->offset + (u64)info.block_size)
                <= 0) {
                    SLOGE("Error writing crypto_blkdev %s for inplace encrypt",data->crypto_blkdev);
                    return -1;
            }
        }
    }
    else {
        if (pwrite64(data->cryptofd, data->buffer,
                     info.block_size * data->count, data->offset)
            <= 0) {
            SLOGE("Error writing crypto_blkdev %s for inplace encrypt",
                  data->crypto_blkdev);
            return -1;
        } else {
          log_progress(data, false);
        }
    }

    data->count = 0;
    data->last_written_sector = (data->offset + data->count)
                                / info.block_size * CRYPT_SECTOR_SIZE - 1;
    return 0;
}

static int encrypt_groups(struct encryptGroupsData* data)
{
    unsigned int i;
    u8 *block_bitmap = 0;
    unsigned int block;
    off64_t ret;
    int rc = -1;

    data->buffer = malloc(info.block_size * BLOCKS_AT_A_TIME);
    if (!data->buffer) {
        SLOGE("Failed to allocate crypto buffer");
        goto errout;
    }

    block_bitmap = malloc(info.block_size);
    if (!block_bitmap) {
        SLOGE("failed to allocate block bitmap");
        goto errout;
    }

    for (i = 0; i < aux_info.groups; ++i) {
        SLOGI("Encrypting group %d", i);

        u32 first_block = aux_info.first_data_block + i * info.blocks_per_group;
        u32 block_count = min(info.blocks_per_group,
                             aux_info.len_blocks - first_block);

        off64_t offset = (u64)info.block_size
                         * aux_info.bg_desc[i].bg_block_bitmap;

        ret = pread64(data->realfd, block_bitmap, info.block_size, offset);
        if (ret != (int)info.block_size) {
            SLOGE("failed to read all of block group bitmap %d", i);
            goto errout;
        }

        offset = (u64)info.block_size * first_block;

        data->count = 0;

        for (block = 0; block < block_count; block++) {
            int used = bitmap_get_bit(block_bitmap, block);
            if (block && (block % 128 == 0)) {
                fsync(data->cryptofd);
            }
            update_progress(data, used);
            if (used) {
                if (data->count == 0) {
                    data->offset = offset;
                }
                data->count++;
            } else {
                if (flush_outstanding_data(data)) {
                    goto errout;
                }
            }

            offset += info.block_size;

            /* Write data if we are aligned or buffer size reached */
            if (offset % (info.block_size * BLOCKS_AT_A_TIME) == 0
                || data->count == BLOCKS_AT_A_TIME) {
                if (flush_outstanding_data(data)) {
                    goto errout;
                }
            }

            if (!is_battery_ok_to_continue()) {
                SLOGE("Stopping encryption due to low battery");
                rc = 0;
                goto errout;
            }

        }
        if (flush_outstanding_data(data)) {
            goto errout;
        }
    }

    data->completed = 1;
    rc = 0;

errout:
    log_progress(0, true);
    free(data->buffer);
    free(block_bitmap);
    return rc;
}

static int cryptfs_enable_inplace_ext4(char *crypto_blkdev,
                                       char *real_blkdev,
                                       off64_t size,
                                       off64_t *size_already_done,
                                       off64_t tot_size,
                                       off64_t previously_encrypted_upto)
{
    u32 i;
    struct encryptGroupsData data;
    int rc; // Can't initialize without causing warning -Wclobbered

    if (previously_encrypted_upto > *size_already_done) {
        SLOGD("Not fast encrypting since resuming part way through");
        return -1;
    }

    memset(&data, 0, sizeof(data));
    data.real_blkdev = real_blkdev;
    data.crypto_blkdev = crypto_blkdev;

    if ( (data.realfd = open(real_blkdev, O_RDWR|O_CLOEXEC)) < 0) {
        SLOGE("Error opening real_blkdev %s for inplace encrypt. err=%d(%s)\n",
              real_blkdev, errno, strerror(errno));
        rc = -1;
        goto errout;
    }

    if ( (data.cryptofd = open(crypto_blkdev, O_WRONLY|O_CLOEXEC)) < 0) {
        SLOGE("Error opening crypto_blkdev %s for ext4 inplace encrypt. err=%d(%s)\n",
              crypto_blkdev, errno, strerror(errno));
        rc = ENABLE_INPLACE_ERR_DEV;
        goto errout;
    }

    if (setjmp(setjmp_env)) {
        SLOGE("Reading ext4 extent caused an exception\n");
        rc = -1;
        goto errout;
    }

    if (read_ext(data.realfd, 0) != 0) {
        SLOGE("Failed to read ext4 extent\n");
        rc = -1;
        goto errout;
    }

    data.numblocks = size / CRYPT_SECTORS_PER_BUFSIZE;
    data.tot_numblocks = tot_size / CRYPT_SECTORS_PER_BUFSIZE;
    data.blocks_already_done = *size_already_done / CRYPT_SECTORS_PER_BUFSIZE;

    SLOGI("Encrypting ext4 filesystem in place...");

    data.tot_used_blocks = data.numblocks;
    for (i = 0; i < aux_info.groups; ++i) {
      data.tot_used_blocks -= aux_info.bg_desc[i].bg_free_blocks_count;
    }

    data.one_pct = data.tot_used_blocks / 100;
    data.cur_pct = 0;

    struct timespec time_started = {0};
    if (clock_gettime(CLOCK_MONOTONIC, &time_started)) {
        SLOGW("Error getting time at start");
        // Note - continue anyway - we'll run with 0
    }
    data.time_started = time_started.tv_sec;
    data.remaining_time = -1;

    rc = encrypt_groups(&data);
    if (rc) {
        SLOGE("Error encrypting groups");
        goto errout;
    }

    *size_already_done += data.completed ? size : data.last_written_sector;
    rc = 0;

errout:
    close(data.realfd);
    close(data.cryptofd);

    return rc;
}

static void log_progress_f2fs(u64 block, bool completed)
{
    // Precondition - if completed data = 0 else data != 0

    // Track progress so we can skip logging blocks
    static u64 last_block = (u64)-1;

    // Need to close existing 'Encrypting from' log?
    if (completed || (last_block != (u64)-1 && block != last_block + 1)) {
        SLOGI("Encrypted to block %" PRId64, last_block);
        last_block = -1;
    }

    // Need to start new 'Encrypting from' log?
    if (!completed && (last_block == (u64)-1 || block != last_block + 1)) {
        SLOGI("Encrypting from block %" PRId64, block);
    }

    // Update offset
    if (!completed) {
        last_block = block;
    }
}

static int encrypt_one_block_f2fs(u64 pos, void *data)
{
    struct encryptGroupsData *priv_dat = (struct encryptGroupsData *)data;

    priv_dat->blocks_already_done = pos - 1;
    update_progress(priv_dat, 1);

    off64_t offset = pos * CRYPT_INPLACE_BUFSIZE;

    if (pread64(priv_dat->realfd, priv_dat->buffer, CRYPT_INPLACE_BUFSIZE, offset) <= 0) {
        SLOGE("Error reading real_blkdev %s for f2fs inplace encrypt", priv_dat->crypto_blkdev);
        return -1;
    }

    if (pwrite64(priv_dat->cryptofd, priv_dat->buffer, CRYPT_INPLACE_BUFSIZE, offset) <= 0) {
        SLOGE("Error writing crypto_blkdev %s for f2fs inplace encrypt", priv_dat->crypto_blkdev);
        return -1;
    } else {
        log_progress_f2fs(pos, false);
    }

    return 0;
}

static int cryptfs_enable_inplace_f2fs(char *crypto_blkdev,
                                       char *real_blkdev,
                                       off64_t size,
                                       off64_t *size_already_done,
                                       off64_t tot_size,
                                       off64_t previously_encrypted_upto)
{
    struct encryptGroupsData data;
    struct f2fs_info *f2fs_info = NULL;
    int rc = ENABLE_INPLACE_ERR_OTHER;
    if (previously_encrypted_upto > *size_already_done) {
        SLOGD("Not fast encrypting since resuming part way through");
        return ENABLE_INPLACE_ERR_OTHER;
    }
    memset(&data, 0, sizeof(data));
    data.real_blkdev = real_blkdev;
    data.crypto_blkdev = crypto_blkdev;
    data.realfd = -1;
    data.cryptofd = -1;
    if ( (data.realfd = open64(real_blkdev, O_RDWR|O_CLOEXEC)) < 0) {
        SLOGE("Error opening real_blkdev %s for f2fs inplace encrypt\n",
              real_blkdev);
        goto errout;
    }
    if ( (data.cryptofd = open64(crypto_blkdev, O_WRONLY|O_CLOEXEC)) < 0) {
        SLOGE("Error opening crypto_blkdev %s for f2fs inplace encrypt. err=%d(%s)\n",
              crypto_blkdev, errno, strerror(errno));
        rc = ENABLE_INPLACE_ERR_DEV;
        goto errout;
    }

    f2fs_info = generate_f2fs_info(data.realfd);
    if (!f2fs_info)
      goto errout;

    data.numblocks = size / CRYPT_SECTORS_PER_BUFSIZE;
    data.tot_numblocks = tot_size / CRYPT_SECTORS_PER_BUFSIZE;
    data.blocks_already_done = *size_already_done / CRYPT_SECTORS_PER_BUFSIZE;

    data.tot_used_blocks = get_num_blocks_used(f2fs_info);

    data.one_pct = data.tot_used_blocks / 100;
    data.cur_pct = 0;
    data.time_started = time(NULL);
    data.remaining_time = -1;

    data.buffer = malloc(f2fs_info->block_size);
    if (!data.buffer) {
        SLOGE("Failed to allocate crypto buffer");
        goto errout;
    }

    data.count = 0;

    /* Currently, this either runs to completion, or hits a nonrecoverable error */
    rc = run_on_used_blocks(data.blocks_already_done, f2fs_info, &encrypt_one_block_f2fs, &data);

    if (rc) {
        SLOGE("Error in running over f2fs blocks");
        rc = ENABLE_INPLACE_ERR_OTHER;
        goto errout;
    }

    *size_already_done += size;
    rc = 0;

errout:
    if (rc)
        SLOGE("Failed to encrypt f2fs filesystem on %s", real_blkdev);

    log_progress_f2fs(0, true);
    free(f2fs_info);
    free(data.buffer);
    close(data.realfd);
    close(data.cryptofd);

    return rc;
}

static int cryptfs_enable_inplace_full(char *crypto_blkdev, char *real_blkdev,
                                       off64_t size, off64_t *size_already_done,
                                       off64_t tot_size,
                                       off64_t previously_encrypted_upto)
{
    int realfd, cryptofd;
    char *buf[CRYPT_INPLACE_BUFSIZE];
    int rc = ENABLE_INPLACE_ERR_OTHER;
    off64_t numblocks, i, remainder;
    off64_t one_pct, cur_pct, new_pct;
    off64_t blocks_already_done, tot_numblocks;

    if ( (realfd = open(real_blkdev, O_RDONLY|O_CLOEXEC)) < 0) {
        SLOGE("Error opening real_blkdev %s for inplace encrypt\n", real_blkdev);
        return ENABLE_INPLACE_ERR_OTHER;
    }

    if ( (cryptofd = open(crypto_blkdev, O_WRONLY|O_CLOEXEC)) < 0) {
        SLOGE("Error opening crypto_blkdev %s for inplace encrypt. err=%d(%s)\n",
              crypto_blkdev, errno, strerror(errno));
        close(realfd);
        return ENABLE_INPLACE_ERR_DEV;
    }

    /* This is pretty much a simple loop of reading 4K, and writing 4K.
     * The size passed in is the number of 512 byte sectors in the filesystem.
     * So compute the number of whole 4K blocks we should read/write,
     * and the remainder.
     */
    numblocks = size / CRYPT_SECTORS_PER_BUFSIZE;
    remainder = size % CRYPT_SECTORS_PER_BUFSIZE;
    tot_numblocks = tot_size / CRYPT_SECTORS_PER_BUFSIZE;
    blocks_already_done = *size_already_done / CRYPT_SECTORS_PER_BUFSIZE;

    SLOGE("Encrypting filesystem in place...");

    i = previously_encrypted_upto + 1 - *size_already_done;

    if (lseek64(realfd, i * CRYPT_SECTOR_SIZE, SEEK_SET) < 0) {
        SLOGE("Cannot seek to previously encrypted point on %s", real_blkdev);
        goto errout;
    }

    if (lseek64(cryptofd, i * CRYPT_SECTOR_SIZE, SEEK_SET) < 0) {
        SLOGE("Cannot seek to previously encrypted point on %s", crypto_blkdev);
        goto errout;
    }

    for (;i < size && i % CRYPT_SECTORS_PER_BUFSIZE != 0; ++i) {
        if (unix_read(realfd, buf, CRYPT_SECTOR_SIZE) <= 0) {
            SLOGE("Error reading initial sectors from real_blkdev %s for "
                  "inplace encrypt\n", crypto_blkdev);
            goto errout;
        }
        if (unix_write(cryptofd, buf, CRYPT_SECTOR_SIZE) <= 0) {
            SLOGE("Error writing initial sectors to crypto_blkdev %s for "
                  "inplace encrypt\n", crypto_blkdev);
            goto errout;
        } else {
            SLOGI("Encrypted 1 block at %" PRId64, i);
        }
    }

    one_pct = tot_numblocks / 100;
    cur_pct = 0;
    /* process the majority of the filesystem in blocks */
    for (i/=CRYPT_SECTORS_PER_BUFSIZE; i<numblocks; i++) {
        new_pct = (i + blocks_already_done) / one_pct;
        if (new_pct > cur_pct) {
            char buf[8];

            cur_pct = new_pct;
            snprintf(buf, sizeof(buf), "%" PRId64, cur_pct);
            property_set("vold.encrypt_progress", buf);
        }
        if (unix_read(realfd, buf, CRYPT_INPLACE_BUFSIZE) <= 0) {
            SLOGE("Error reading real_blkdev %s for inplace encrypt", crypto_blkdev);
            goto errout;
        }
        if (unix_write(cryptofd, buf, CRYPT_INPLACE_BUFSIZE) <= 0) {
            SLOGE("Error writing crypto_blkdev %s for inplace encrypt", crypto_blkdev);
            goto errout;
        } else {
            SLOGD("Encrypted %d block at %" PRId64,
                  CRYPT_SECTORS_PER_BUFSIZE,
                  i * CRYPT_SECTORS_PER_BUFSIZE);
        }

       if (!is_battery_ok_to_continue()) {
            SLOGE("Stopping encryption due to low battery");
            *size_already_done += (i + 1) * CRYPT_SECTORS_PER_BUFSIZE - 1;
            rc = 0;
            goto errout;
        }
    }

    /* Do any remaining sectors */
    for (i=0; i<remainder; i++) {
        if (unix_read(realfd, buf, CRYPT_SECTOR_SIZE) <= 0) {
            SLOGE("Error reading final sectors from real_blkdev %s for inplace encrypt", crypto_blkdev);
            goto errout;
        }
        if (unix_write(cryptofd, buf, CRYPT_SECTOR_SIZE) <= 0) {
            SLOGE("Error writing final sectors to crypto_blkdev %s for inplace encrypt", crypto_blkdev);
            goto errout;
        } else {
            SLOGI("Encrypted 1 block at next location");
        }
    }

    *size_already_done += size;
    rc = 0;

errout:
    close(realfd);
    close(cryptofd);

    return rc;
}

/* returns on of the ENABLE_INPLACE_* return codes */
static int cryptfs_enable_inplace(char *crypto_blkdev, char *real_blkdev,
                                  off64_t size, off64_t *size_already_done,
                                  off64_t tot_size,
                                  off64_t previously_encrypted_upto)
{
    int rc_ext4, rc_f2fs, rc_full;
    if (previously_encrypted_upto) {
        SLOGD("Continuing encryption from %" PRId64, previously_encrypted_upto);
    }

    if (*size_already_done + size < previously_encrypted_upto) {
        *size_already_done += size;
        return 0;
    }

    /* TODO: identify filesystem type.
     * As is, cryptfs_enable_inplace_ext4 will fail on an f2fs partition, and
     * then we will drop down to cryptfs_enable_inplace_f2fs.
     * */
    if ((rc_ext4 = cryptfs_enable_inplace_ext4(crypto_blkdev, real_blkdev,
                                size, size_already_done,
                                tot_size, previously_encrypted_upto)) == 0) {
      return 0;
    }
    SLOGD("cryptfs_enable_inplace_ext4()=%d\n", rc_ext4);

    if ((rc_f2fs = cryptfs_enable_inplace_f2fs(crypto_blkdev, real_blkdev,
                                size, size_already_done,
                                tot_size, previously_encrypted_upto)) == 0) {
      return 0;
    }
    SLOGD("cryptfs_enable_inplace_f2fs()=%d\n", rc_f2fs);

    rc_full = cryptfs_enable_inplace_full(crypto_blkdev, real_blkdev,
                                       size, size_already_done, tot_size,
                                       previously_encrypted_upto);
    SLOGD("cryptfs_enable_inplace_full()=%d\n", rc_full);

    /* Hack for b/17898962, the following is the symptom... */
    if (rc_ext4 == ENABLE_INPLACE_ERR_DEV
        && rc_f2fs == ENABLE_INPLACE_ERR_DEV
        && rc_full == ENABLE_INPLACE_ERR_DEV) {
            return ENABLE_INPLACE_ERR_DEV;
    }
    return rc_full;
}

#define CRYPTO_ENABLE_WIPE 1
#define CRYPTO_ENABLE_INPLACE 2

#define FRAMEWORK_BOOT_WAIT 60

static int cryptfs_SHA256_fileblock(const char* filename, __le8* buf)
{
    int fd = open(filename, O_RDONLY|O_CLOEXEC);
    if (fd == -1) {
        SLOGE("Error opening file %s", filename);
        return -1;
    }

    char block[CRYPT_INPLACE_BUFSIZE];
    memset(block, 0, sizeof(block));
    if (unix_read(fd, block, sizeof(block)) < 0) {
        SLOGE("Error reading file %s", filename);
        close(fd);
        return -1;
    }

    close(fd);

    SHA256_CTX c;
    SHA256_Init(&c);
    SHA256_Update(&c, block, sizeof(block));
    SHA256_Final(buf, &c);

    return 0;
}

static int get_fs_type(struct fstab_rec *rec)
{
    if (!strcmp(rec->fs_type, "ext4")) {
        return EXT4_FS;
    } else if (!strcmp(rec->fs_type, "f2fs")) {
        return F2FS_FS;
    } else {
        return -1;
    }
}

static int cryptfs_enable_all_volumes(struct crypt_mnt_ftr *crypt_ftr, int how,
                                      char *crypto_blkdev, char *real_blkdev,
                                      int previously_encrypted_upto)
{
    off64_t cur_encryption_done=0, tot_encryption_size=0;
    int rc = -1;

    if (!is_battery_ok_to_start()) {
        SLOGW("Not starting encryption due to low battery");
        return 0;
    }

    /* The size of the userdata partition, and add in the vold volumes below */
    tot_encryption_size = crypt_ftr->fs_size;

    if (how == CRYPTO_ENABLE_WIPE) {
        struct fstab_rec* rec = fs_mgr_get_entry_for_mount_point(fstab, DATA_MNT_POINT);
        int fs_type = get_fs_type(rec);
        if (fs_type < 0) {
            SLOGE("cryptfs_enable: unsupported fs type %s\n", rec->fs_type);
            return -1;
        }
        rc = cryptfs_enable_wipe(crypto_blkdev, crypt_ftr->fs_size, fs_type);
    } else if (how == CRYPTO_ENABLE_INPLACE) {
        rc = cryptfs_enable_inplace(crypto_blkdev, real_blkdev,
                                    crypt_ftr->fs_size, &cur_encryption_done,
                                    tot_encryption_size,
                                    previously_encrypted_upto);

        if (rc == ENABLE_INPLACE_ERR_DEV) {
            /* Hack for b/17898962 */
            SLOGE("cryptfs_enable: crypto block dev failure. Must reboot...\n");
            cryptfs_reboot(reboot);
        }

        if (!rc) {
            crypt_ftr->encrypted_upto = cur_encryption_done;
        }

        if (!rc && crypt_ftr->encrypted_upto == crypt_ftr->fs_size) {
            /* The inplace routine never actually sets the progress to 100% due
             * to the round down nature of integer division, so set it here */
            property_set("vold.encrypt_progress", "100");
        }
    } else {
        /* Shouldn't happen */
        SLOGE("cryptfs_enable: internal error, unknown option\n");
        rc = -1;
    }

    return rc;
}

int cryptfs_enable_internal(char *howarg, int crypt_type, char *passwd,
                            int allow_reboot)
{
    int how = 0;
    char crypto_blkdev[MAXPATHLEN], real_blkdev[MAXPATHLEN];
    unsigned char decrypted_master_key[KEY_LEN_BYTES];
    int rc=-1, i;
    struct crypt_mnt_ftr crypt_ftr;
    struct crypt_persist_data *pdata;
    char encrypted_state[PROPERTY_VALUE_MAX];
    char lockid[32] = { 0 };
    char key_loc[PROPERTY_VALUE_MAX];
    int num_vols;
    off64_t previously_encrypted_upto = 0;

    if (!strcmp(howarg, "wipe")) {
      how = CRYPTO_ENABLE_WIPE;
    } else if (! strcmp(howarg, "inplace")) {
      how = CRYPTO_ENABLE_INPLACE;
    } else {
      /* Shouldn't happen, as CommandListener vets the args */
      goto error_unencrypted;
    }

    /* See if an encryption was underway and interrupted */
    if (how == CRYPTO_ENABLE_INPLACE
          && get_crypt_ftr_and_key(&crypt_ftr) == 0
          && (crypt_ftr.flags & CRYPT_ENCRYPTION_IN_PROGRESS)) {
        previously_encrypted_upto = crypt_ftr.encrypted_upto;
        crypt_ftr.encrypted_upto = 0;
        crypt_ftr.flags &= ~CRYPT_ENCRYPTION_IN_PROGRESS;

        /* At this point, we are in an inconsistent state. Until we successfully
           complete encryption, a reboot will leave us broken. So mark the
           encryption failed in case that happens.
           On successfully completing encryption, remove this flag */
        crypt_ftr.flags |= CRYPT_INCONSISTENT_STATE;

        put_crypt_ftr_and_key(&crypt_ftr);
    }

    property_get("ro.crypto.state", encrypted_state, "");
    if (!strcmp(encrypted_state, "encrypted") && !previously_encrypted_upto) {
        SLOGE("Device is already running encrypted, aborting");
        goto error_unencrypted;
    }

    // TODO refactor fs_mgr_get_crypt_info to get both in one call
    fs_mgr_get_crypt_info(fstab, key_loc, 0, sizeof(key_loc));
    fs_mgr_get_crypt_info(fstab, 0, real_blkdev, sizeof(real_blkdev));

    /* Get the size of the real block device */
    int fd = open(real_blkdev, O_RDONLY|O_CLOEXEC);
    if (fd == -1) {
        SLOGE("Cannot open block device %s\n", real_blkdev);
        goto error_unencrypted;
    }
    unsigned long nr_sec;
    get_blkdev_size(fd, &nr_sec);
    if (nr_sec == 0) {
        SLOGE("Cannot get size of block device %s\n", real_blkdev);
        goto error_unencrypted;
    }
    close(fd);

    /* If doing inplace encryption, make sure the orig fs doesn't include the crypto footer */
    if ((how == CRYPTO_ENABLE_INPLACE) && (!strcmp(key_loc, KEY_IN_FOOTER))) {
        unsigned int fs_size_sec, max_fs_size_sec;
        fs_size_sec = get_fs_size(real_blkdev);
        if (fs_size_sec == 0)
            fs_size_sec = get_f2fs_filesystem_size_sec(real_blkdev);

        max_fs_size_sec = nr_sec - (CRYPT_FOOTER_OFFSET / CRYPT_SECTOR_SIZE);

        if (fs_size_sec > max_fs_size_sec) {
            SLOGE("Orig filesystem overlaps crypto footer region.  Cannot encrypt in place.");
            goto error_unencrypted;
        }
    }

    /* Get a wakelock as this may take a while, and we don't want the
     * device to sleep on us.  We'll grab a partial wakelock, and if the UI
     * wants to keep the screen on, it can grab a full wakelock.
     */
    snprintf(lockid, sizeof(lockid), "enablecrypto%d", (int) getpid());
    acquire_wake_lock(PARTIAL_WAKE_LOCK, lockid);

    /* The init files are setup to stop the class main and late start when
     * vold sets trigger_shutdown_framework.
     */
    property_set("vold.decrypt", "trigger_shutdown_framework");
    SLOGD("Just asked init to shut down class main\n");

    /* Ask vold to unmount all devices that it manages */
    if (vold_unmountAll()) {
        SLOGE("Failed to unmount all vold managed devices");
    }

    /* Now unmount the /data partition. */
    if (wait_and_unmount(DATA_MNT_POINT, true)) {
        if (allow_reboot) {
            goto error_shutting_down;
        } else {
            goto error_unencrypted;
        }
    }

    /* Do extra work for a better UX when doing the long inplace encryption */
    if (how == CRYPTO_ENABLE_INPLACE) {
        /* Now that /data is unmounted, we need to mount a tmpfs
         * /data, set a property saying we're doing inplace encryption,
         * and restart the framework.
         */
        if (fs_mgr_do_tmpfs_mount(DATA_MNT_POINT)) {
            goto error_shutting_down;
        }
        /* Tells the framework that inplace encryption is starting */
        property_set("vold.encrypt_progress", "0");

        /* restart the framework. */
        /* Create necessary paths on /data */
        if (prep_data_fs()) {
            goto error_shutting_down;
        }

        /* Ugh, shutting down the framework is not synchronous, so until it
         * can be fixed, this horrible hack will wait a moment for it all to
         * shut down before proceeding.  Without it, some devices cannot
         * restart the graphics services.
         */
        sleep(2);
    }

    /* Start the actual work of making an encrypted filesystem */
    /* Initialize a crypt_mnt_ftr for the partition */
    if (previously_encrypted_upto == 0) {
        if (cryptfs_init_crypt_mnt_ftr(&crypt_ftr)) {
            goto error_shutting_down;
        }

        if (!strcmp(key_loc, KEY_IN_FOOTER)) {
            crypt_ftr.fs_size = nr_sec
              - (CRYPT_FOOTER_OFFSET / CRYPT_SECTOR_SIZE);
        } else {
            crypt_ftr.fs_size = nr_sec;
        }
        /* At this point, we are in an inconsistent state. Until we successfully
           complete encryption, a reboot will leave us broken. So mark the
           encryption failed in case that happens.
           On successfully completing encryption, remove this flag */
        crypt_ftr.flags |= CRYPT_INCONSISTENT_STATE;
        crypt_ftr.crypt_type = crypt_type;
#ifndef CONFIG_HW_DISK_ENCRYPTION
        strlcpy((char *)crypt_ftr.crypto_type_name, "aes-cbc-essiv:sha256", MAX_CRYPTO_TYPE_NAME_LEN);
#else
        strlcpy((char *)crypt_ftr.crypto_type_name, "aes-xts", MAX_CRYPTO_TYPE_NAME_LEN);

        rc = clear_hw_device_encryption_key();
        if (!rc) {
          SLOGE("Error clearing device encryption hardware key. rc = %d", rc);
        }

        rc = set_hw_device_encryption_key(passwd,
                                          (char*) crypt_ftr.crypto_type_name);
        if (!rc) {
          SLOGE("Error initializing device encryption hardware key. rc = %d", rc);
          goto error_shutting_down;
        }
#endif

        /* Make an encrypted master key */
        if (create_encrypted_random_key(passwd, crypt_ftr.master_key, crypt_ftr.salt, &crypt_ftr)) {
            SLOGE("Cannot create encrypted master key\n");
            goto error_shutting_down;
        }

        /* Write the key to the end of the partition */
        put_crypt_ftr_and_key(&crypt_ftr);

        /* If any persistent data has been remembered, save it.
         * If none, create a valid empty table and save that.
         */
        if (!persist_data) {
           pdata = malloc(CRYPT_PERSIST_DATA_SIZE);
           if (pdata) {
               init_empty_persist_data(pdata, CRYPT_PERSIST_DATA_SIZE);
               persist_data = pdata;
           }
        }
        if (persist_data) {
            save_persistent_data();
        }
    }

    if (how == CRYPTO_ENABLE_INPLACE) {
        /* startup service classes main and late_start */
        property_set("vold.decrypt", "trigger_restart_min_framework");
        SLOGD("Just triggered restart_min_framework\n");

        /* OK, the framework is restarted and will soon be showing a
         * progress bar.  Time to setup an encrypted mapping, and
         * either write a new filesystem, or encrypt in place updating
         * the progress bar as we work.
         */
    }

    decrypt_master_key(passwd, decrypted_master_key, &crypt_ftr, 0, 0);
    create_crypto_blk_dev(&crypt_ftr, decrypted_master_key, real_blkdev, crypto_blkdev,
                          "userdata");

    /* If we are continuing, check checksums match */
    rc = 0;
    if (previously_encrypted_upto) {
        __le8 hash_first_block[SHA256_DIGEST_LENGTH];
        rc = cryptfs_SHA256_fileblock(crypto_blkdev, hash_first_block);

        if (!rc && memcmp(hash_first_block, crypt_ftr.hash_first_block,
                          sizeof(hash_first_block)) != 0) {
            SLOGE("Checksums do not match - trigger wipe");
            rc = -1;
        }
    }

    if (!rc) {
        rc = cryptfs_enable_all_volumes(&crypt_ftr, how,
                                        crypto_blkdev, real_blkdev,
                                        previously_encrypted_upto);
    }

    /* Calculate checksum if we are not finished */
    if (!rc && how == CRYPTO_ENABLE_INPLACE
            && crypt_ftr.encrypted_upto != crypt_ftr.fs_size) {
        rc = cryptfs_SHA256_fileblock(crypto_blkdev,
                                      crypt_ftr.hash_first_block);
        if (rc) {
            SLOGE("Error calculating checksum for continuing encryption");
            rc = -1;
        }
    }

    /* Undo the dm-crypt mapping whether we succeed or not */
    delete_crypto_blk_dev("userdata");

    if (! rc) {
        /* Success */
        crypt_ftr.flags &= ~CRYPT_INCONSISTENT_STATE;

        if (how == CRYPTO_ENABLE_INPLACE
              && crypt_ftr.encrypted_upto != crypt_ftr.fs_size) {
            SLOGD("Encrypted up to sector %lld - will continue after reboot",
                  crypt_ftr.encrypted_upto);
            crypt_ftr.flags |= CRYPT_ENCRYPTION_IN_PROGRESS;
        }

        put_crypt_ftr_and_key(&crypt_ftr);

        if (how == CRYPTO_ENABLE_WIPE
              || crypt_ftr.encrypted_upto == crypt_ftr.fs_size) {
          char value[PROPERTY_VALUE_MAX];
          property_get("ro.crypto.state", value, "");
          if (!strcmp(value, "")) {
            /* default encryption - continue first boot sequence */
            property_set("ro.crypto.state", "encrypted");
            release_wake_lock(lockid);
            cryptfs_check_passwd(DEFAULT_PASSWORD);
            cryptfs_restart_internal(1);
            return 0;
          } else {
            sleep(2); /* Give the UI a chance to show 100% progress */
            cryptfs_reboot(reboot);
          }
        } else {
            sleep(2); /* Partially encrypted, ensure writes flushed to ssd */
            cryptfs_reboot(shutdown);
        }
    } else {
        char value[PROPERTY_VALUE_MAX];

        property_get("ro.vold.wipe_on_crypt_fail", value, "0");
        if (!strcmp(value, "1")) {
            /* wipe data if encryption failed */
            SLOGE("encryption failed - rebooting into recovery to wipe data\n");
            mkdir("/cache/recovery", 0700);
            int fd = open("/cache/recovery/command", O_RDWR|O_CREAT|O_TRUNC|O_CLOEXEC, 0600);
            if (fd >= 0) {
                write(fd, "--wipe_data\n", strlen("--wipe_data\n") + 1);
                write(fd, "--reason=cryptfs_enable_internal\n", strlen("--reason=cryptfs_enable_internal\n") + 1);
                close(fd);
            } else {
                SLOGE("could not open /cache/recovery/command\n");
            }
            cryptfs_reboot(recovery);
        } else {
            /* set property to trigger dialog */
            property_set("vold.encrypt_progress", "error_partially_encrypted");
            release_wake_lock(lockid);
        }
        return -1;
    }

    /* hrm, the encrypt step claims success, but the reboot failed.
     * This should not happen.
     * Set the property and return.  Hope the framework can deal with it.
     */
    property_set("vold.encrypt_progress", "error_reboot_failed");
    release_wake_lock(lockid);
    return rc;

error_unencrypted:
    property_set("vold.encrypt_progress", "error_not_encrypted");
    if (lockid[0]) {
        release_wake_lock(lockid);
    }
    return -1;

error_shutting_down:
    /* we failed, and have not encrypted anthing, so the users's data is still intact,
     * but the framework is stopped and not restarted to show the error, so it's up to
     * vold to restart the system.
     */
    SLOGE("Error enabling encryption after framework is shutdown, no data changed, restarting system");
    cryptfs_reboot(reboot);

    /* shouldn't get here */
    property_set("vold.encrypt_progress", "error_shutting_down");
    if (lockid[0]) {
        release_wake_lock(lockid);
    }
    return -1;
}

int cryptfs_enable(char *howarg, int type, char *passwd, int allow_reboot)
{
    return cryptfs_enable_internal(howarg, type, passwd, allow_reboot);
}

int cryptfs_enable_default(char *howarg, int allow_reboot)
{
    return cryptfs_enable_internal(howarg, CRYPT_TYPE_DEFAULT,
                          DEFAULT_PASSWORD, allow_reboot);
}

int cryptfs_changepw(int crypt_type, const char *newpw)
{
    if (e4crypt_crypto_complete(DATA_MNT_POINT) == 0) {
        return e4crypt_change_password(DATA_MNT_POINT, crypt_type, newpw);
    }

    struct crypt_mnt_ftr crypt_ftr;
    int rc;

    /* This is only allowed after we've successfully decrypted the master key */
    if (!master_key_saved) {
        SLOGE("Key not saved, aborting");
        return -1;
    }

    if (crypt_type < 0 || crypt_type > CRYPT_TYPE_MAX_TYPE) {
        SLOGE("Invalid crypt_type %d", crypt_type);
        return -1;
    }

    /* get key */
    if (get_crypt_ftr_and_key(&crypt_ftr)) {
        SLOGE("Error getting crypt footer and key");
        return -1;
    }

    crypt_ftr.crypt_type = crypt_type;

    rc = encrypt_master_key(crypt_type == CRYPT_TYPE_DEFAULT ? DEFAULT_PASSWORD
                                                        : newpw,
                       crypt_ftr.salt,
                       saved_master_key,
                       crypt_ftr.master_key,
                       &crypt_ftr);
    if (rc) {
        SLOGE("Encrypt master key failed: %d", rc);
        return -1;
    }
    /* save the key */
    put_crypt_ftr_and_key(&crypt_ftr);

#ifdef CONFIG_HW_DISK_ENCRYPTION
    if (!strcmp((char *)crypt_ftr.crypto_type_name, "aes-xts")) {
        if (crypt_type == CRYPT_TYPE_DEFAULT) {
            int rc = update_hw_device_encryption_key(DEFAULT_PASSWORD, (char*) crypt_ftr.crypto_type_name);
            SLOGD("Update hardware encryption key to default for crypt_type: %d. rc = %d", crypt_type, rc);
            if (!rc)
                return -1;
        } else {
            int rc = update_hw_device_encryption_key(newpw, (char*) crypt_ftr.crypto_type_name);
            SLOGD("Update hardware encryption key for crypt_type: %d. rc = %d", crypt_type, rc);
            if (!rc)
                return -1;
        }
    }
#endif
    return 0;
}

static unsigned int persist_get_max_entries(int encrypted) {
    struct crypt_mnt_ftr crypt_ftr;
    unsigned int dsize;
    unsigned int max_persistent_entries;

    /* If encrypted, use the values from the crypt_ftr, otherwise
     * use the values for the current spec.
     */
    if (encrypted) {
        if (get_crypt_ftr_and_key(&crypt_ftr)) {
            return -1;
        }
        dsize = crypt_ftr.persist_data_size;
    } else {
        dsize = CRYPT_PERSIST_DATA_SIZE;
    }

    max_persistent_entries = (dsize - sizeof(struct crypt_persist_data)) /
        sizeof(struct crypt_persist_entry);

    return max_persistent_entries;
}

static int persist_get_key(const char *fieldname, char *value)
{
    unsigned int i;

    if (persist_data == NULL) {
        return -1;
    }
    for (i = 0; i < persist_data->persist_valid_entries; i++) {
        if (!strncmp(persist_data->persist_entry[i].key, fieldname, PROPERTY_KEY_MAX)) {
            /* We found it! */
            strlcpy(value, persist_data->persist_entry[i].val, PROPERTY_VALUE_MAX);
            return 0;
        }
    }

    return -1;
}

static int persist_set_key(const char *fieldname, const char *value, int encrypted)
{
    unsigned int i;
    unsigned int num;
    unsigned int max_persistent_entries;

    if (persist_data == NULL) {
        return -1;
    }

    max_persistent_entries = persist_get_max_entries(encrypted);

    num = persist_data->persist_valid_entries;

    for (i = 0; i < num; i++) {
        if (!strncmp(persist_data->persist_entry[i].key, fieldname, PROPERTY_KEY_MAX)) {
            /* We found an existing entry, update it! */
            memset(persist_data->persist_entry[i].val, 0, PROPERTY_VALUE_MAX);
            strlcpy(persist_data->persist_entry[i].val, value, PROPERTY_VALUE_MAX);
            return 0;
        }
    }

    /* We didn't find it, add it to the end, if there is room */
    if (persist_data->persist_valid_entries < max_persistent_entries) {
        memset(&persist_data->persist_entry[num], 0, sizeof(struct crypt_persist_entry));
        strlcpy(persist_data->persist_entry[num].key, fieldname, PROPERTY_KEY_MAX);
        strlcpy(persist_data->persist_entry[num].val, value, PROPERTY_VALUE_MAX);
        persist_data->persist_valid_entries++;
        return 0;
    }

    return -1;
}

/**
 * Test if key is part of the multi-entry (field, index) sequence. Return non-zero if key is in the
 * sequence and its index is greater than or equal to index. Return 0 otherwise.
 */
static int match_multi_entry(const char *key, const char *field, unsigned index) {
    unsigned int field_len;
    unsigned int key_index;
    field_len = strlen(field);

    if (index == 0) {
        // The first key in a multi-entry field is just the filedname itself.
        if (!strcmp(key, field)) {
            return 1;
        }
    }
    // Match key against "%s_%d" % (field, index)
    if (strlen(key) < field_len + 1 + 1) {
        // Need at least a '_' and a digit.
        return 0;
    }
    if (strncmp(key, field, field_len)) {
        // If the key does not begin with field, it's not a match.
        return 0;
    }
    if (1 != sscanf(&key[field_len],"_%d", &key_index)) {
        return 0;
    }
    return key_index >= index;
}

/*
 * Delete entry/entries from persist_data. If the entries are part of a multi-segment field, all
 * remaining entries starting from index will be deleted.
 * returns PERSIST_DEL_KEY_OK if deletion succeeds,
 * PERSIST_DEL_KEY_ERROR_NO_FIELD if the field does not exist,
 * and PERSIST_DEL_KEY_ERROR_OTHER if error occurs.
 *
 */
static int persist_del_keys(const char *fieldname, unsigned index)
{
    unsigned int i;
    unsigned int j;
    unsigned int num;

    if (persist_data == NULL) {
        return PERSIST_DEL_KEY_ERROR_OTHER;
    }

    num = persist_data->persist_valid_entries;

    j = 0; // points to the end of non-deleted entries.
    // Filter out to-be-deleted entries in place.
    for (i = 0; i < num; i++) {
        if (!match_multi_entry(persist_data->persist_entry[i].key, fieldname, index)) {
            persist_data->persist_entry[j] = persist_data->persist_entry[i];
            j++;
        }
    }

    if (j < num) {
        persist_data->persist_valid_entries = j;
        // Zeroise the remaining entries
        memset(&persist_data->persist_entry[j], 0, (num - j) * sizeof(struct crypt_persist_entry));
        return PERSIST_DEL_KEY_OK;
    } else {
        // Did not find an entry matching the given fieldname
        return PERSIST_DEL_KEY_ERROR_NO_FIELD;
    }
}

static int persist_count_keys(const char *fieldname)
{
    unsigned int i;
    unsigned int count;

    if (persist_data == NULL) {
        return -1;
    }

    count = 0;
    for (i = 0; i < persist_data->persist_valid_entries; i++) {
        if (match_multi_entry(persist_data->persist_entry[i].key, fieldname, 0)) {
            count++;
        }
    }

    return count;
}

/* Return the value of the specified field. */
int cryptfs_getfield(const char *fieldname, char *value, int len)
{
    if (e4crypt_crypto_complete(DATA_MNT_POINT) == 0) {
        return e4crypt_get_field(DATA_MNT_POINT, fieldname, value, len);
    }

    char temp_value[PROPERTY_VALUE_MAX];
    /* CRYPTO_GETFIELD_OK is success,
     * CRYPTO_GETFIELD_ERROR_NO_FIELD is value not set,
     * CRYPTO_GETFIELD_ERROR_BUF_TOO_SMALL is buffer (as given by len) too small,
     * CRYPTO_GETFIELD_ERROR_OTHER is any other error
     */
    int rc = CRYPTO_GETFIELD_ERROR_OTHER;
    int i;
    char temp_field[PROPERTY_KEY_MAX];

    if (persist_data == NULL) {
        load_persistent_data();
        if (persist_data == NULL) {
            SLOGE("Getfield error, cannot load persistent data");
            goto out;
        }
    }

    // Read value from persistent entries. If the original value is split into multiple entries,
    // stitch them back together.
    if (!persist_get_key(fieldname, temp_value)) {
        // We found it, copy it to the caller's buffer and keep going until all entries are read.
        if (strlcpy(value, temp_value, len) >= (unsigned) len) {
            // value too small
            rc = CRYPTO_GETFIELD_ERROR_BUF_TOO_SMALL;
            goto out;
        }
        rc = CRYPTO_GETFIELD_OK;

        for (i = 1; /* break explicitly */; i++) {
            if (snprintf(temp_field, sizeof(temp_field), "%s_%d", fieldname, i) >=
                    (int) sizeof(temp_field)) {
                // If the fieldname is very long, we stop as soon as it begins to overflow the
                // maximum field length. At this point we have in fact fully read out the original
                // value because cryptfs_setfield would not allow fields with longer names to be
                // written in the first place.
                break;
            }
            if (!persist_get_key(temp_field, temp_value)) {
                  if (strlcat(value, temp_value, len) >= (unsigned)len) {
                      // value too small.
                      rc = CRYPTO_GETFIELD_ERROR_BUF_TOO_SMALL;
                      goto out;
                  }
            } else {
                // Exhaust all entries.
                break;
            }
        }
    } else {
        /* Sadness, it's not there.  Return the error */
        rc = CRYPTO_GETFIELD_ERROR_NO_FIELD;
    }

out:
    return rc;
}

/* Set the value of the specified field. */
int cryptfs_setfield(const char *fieldname, const char *value)
{
    if (e4crypt_crypto_complete(DATA_MNT_POINT) == 0) {
        return e4crypt_set_field(DATA_MNT_POINT, fieldname, value);
    }

    char encrypted_state[PROPERTY_VALUE_MAX];
    /* 0 is success, negative values are error */
    int rc = CRYPTO_SETFIELD_ERROR_OTHER;
    int encrypted = 0;
    unsigned int field_id;
    char temp_field[PROPERTY_KEY_MAX];
    unsigned int num_entries;
    unsigned int max_keylen;

    if (persist_data == NULL) {
        load_persistent_data();
        if (persist_data == NULL) {
            SLOGE("Setfield error, cannot load persistent data");
            goto out;
        }
    }

    property_get("ro.crypto.state", encrypted_state, "");
    if (!strcmp(encrypted_state, "encrypted") ) {
        encrypted = 1;
    }

    // Compute the number of entries required to store value, each entry can store up to
    // (PROPERTY_VALUE_MAX - 1) chars
    if (strlen(value) == 0) {
        // Empty value also needs one entry to store.
        num_entries = 1;
    } else {
        num_entries = (strlen(value) + (PROPERTY_VALUE_MAX - 1) - 1) / (PROPERTY_VALUE_MAX - 1);
    }

    max_keylen = strlen(fieldname);
    if (num_entries > 1) {
        // Need an extra "_%d" suffix.
        max_keylen += 1 + log10(num_entries);
    }
    if (max_keylen > PROPERTY_KEY_MAX - 1) {
        rc = CRYPTO_SETFIELD_ERROR_FIELD_TOO_LONG;
        goto out;
    }

    // Make sure we have enough space to write the new value
    if (persist_data->persist_valid_entries + num_entries - persist_count_keys(fieldname) >
        persist_get_max_entries(encrypted)) {
        rc = CRYPTO_SETFIELD_ERROR_VALUE_TOO_LONG;
        goto out;
    }

    // Now that we know persist_data has enough space for value, let's delete the old field first
    // to make up space.
    persist_del_keys(fieldname, 0);

    if (persist_set_key(fieldname, value, encrypted)) {
        // fail to set key, should not happen as we have already checked the available space
        SLOGE("persist_set_key() error during setfield()");
        goto out;
    }

    for (field_id = 1; field_id < num_entries; field_id++) {
        snprintf(temp_field, sizeof(temp_field), "%s_%d", fieldname, field_id);

        if (persist_set_key(temp_field, value + field_id * (PROPERTY_VALUE_MAX - 1), encrypted)) {
            // fail to set key, should not happen as we have already checked the available space.
            SLOGE("persist_set_key() error during setfield()");
            goto out;
        }
    }

    /* If we are running encrypted, save the persistent data now */
    if (encrypted) {
        if (save_persistent_data()) {
            SLOGE("Setfield error, cannot save persistent data");
            goto out;
        }
    }

    rc = CRYPTO_SETFIELD_OK;

out:
    return rc;
}

/* Checks userdata. Attempt to mount the volume if default-
 * encrypted.
 * On success trigger next init phase and return 0.
 * Currently do not handle failure - see TODO below.
 */
int cryptfs_mount_default_encrypted(void)
{
    char decrypt_state[PROPERTY_VALUE_MAX];
    property_get("vold.decrypt", decrypt_state, "0");
    if (!strcmp(decrypt_state, "0")) {
        SLOGE("Not encrypted - should not call here");
    } else {
        int crypt_type = cryptfs_get_password_type();
        if (crypt_type < 0 || crypt_type > CRYPT_TYPE_MAX_TYPE) {
            SLOGE("Bad crypt type - error");
        } else if (crypt_type != CRYPT_TYPE_DEFAULT) {
            SLOGD("Password is not default - "
                  "starting min framework to prompt");
            property_set("vold.decrypt", "trigger_restart_min_framework");
            return 0;
        } else if (cryptfs_check_passwd(DEFAULT_PASSWORD) == 0) {
            SLOGD("Password is default - restarting filesystem");
            cryptfs_restart_internal(0);
            return 0;
        } else {
            SLOGE("Encrypted, default crypt type but can't decrypt");
        }
    }

    /** Corrupt. Allow us to boot into framework, which will detect bad
        crypto when it calls do_crypto_complete, then do a factory reset
     */
    property_set("vold.decrypt", "trigger_restart_min_framework");
    return 0;
}

/* Returns type of the password, default, pattern, pin or password.
 */
int cryptfs_get_password_type(void)
{
    if (e4crypt_crypto_complete(DATA_MNT_POINT) == 0) {
        return e4crypt_get_password_type(DATA_MNT_POINT);
    }

    struct crypt_mnt_ftr crypt_ftr;

    if (get_crypt_ftr_and_key(&crypt_ftr)) {
        SLOGE("Error getting crypt footer and key\n");
        return -1;
    }

    if (crypt_ftr.flags & CRYPT_INCONSISTENT_STATE) {
        return -1;
    }

    return crypt_ftr.crypt_type;
}

const char* cryptfs_get_password()
{
    if (e4crypt_crypto_complete(DATA_MNT_POINT) == 0) {
        return e4crypt_get_password(DATA_MNT_POINT);
    }

    struct timespec now;
    clock_gettime(CLOCK_BOOTTIME, &now);
    if (now.tv_sec < password_expiry_time) {
        return password;
    } else {
        cryptfs_clear_password();
        return 0;
    }
}

void cryptfs_clear_password()
{
    if (e4crypt_crypto_complete(DATA_MNT_POINT) == 0) {
        e4crypt_clear_password(DATA_MNT_POINT);
    }

    if (password) {
        size_t len = strlen(password);
        memset(password, 0, len);
        free(password);
        password = 0;
        password_expiry_time = 0;
    }
}

int cryptfs_enable_file()
{
    return e4crypt_enable(DATA_MNT_POINT);
}

int cryptfs_create_default_ftr(struct crypt_mnt_ftr* crypt_ftr, __attribute__((unused))int key_length)
{
    if (cryptfs_init_crypt_mnt_ftr(crypt_ftr)) {
        SLOGE("Failed to initialize crypt_ftr");
        return -1;
    }

    if (create_encrypted_random_key(DEFAULT_PASSWORD, crypt_ftr->master_key,
                                    crypt_ftr->salt, crypt_ftr)) {
        SLOGE("Cannot create encrypted master key\n");
        return -1;
    }

    //crypt_ftr->keysize = key_length / 8;
    return 0;
}

int cryptfs_get_master_key(struct crypt_mnt_ftr* ftr, const char* password,
                           unsigned char* master_key)
{
    int rc;

    unsigned char* intermediate_key = 0;
    size_t intermediate_key_size = 0;

    if (password == 0 || *password == 0) {
        password = DEFAULT_PASSWORD;
    }

    rc = decrypt_master_key(password, master_key, ftr, &intermediate_key,
                            &intermediate_key_size);

    int N = 1 << ftr->N_factor;
    int r = 1 << ftr->r_factor;
    int p = 1 << ftr->p_factor;

    unsigned char scrypted_intermediate_key[sizeof(ftr->scrypted_intermediate_key)];

    rc = crypto_scrypt(intermediate_key, intermediate_key_size,
                       ftr->salt, sizeof(ftr->salt), N, r, p,
                       scrypted_intermediate_key,
                       sizeof(scrypted_intermediate_key));

    free(intermediate_key);

    if (rc) {
        SLOGE("Can't calculate intermediate key");
        return rc;
    }

    return memcmp(scrypted_intermediate_key, ftr->scrypted_intermediate_key,
                  intermediate_key_size);
}

int cryptfs_set_password(struct crypt_mnt_ftr* ftr, const char* password,
                         const unsigned char* master_key)
{
    return encrypt_master_key(password, ftr->salt, master_key, ftr->master_key,
                              ftr);
}
