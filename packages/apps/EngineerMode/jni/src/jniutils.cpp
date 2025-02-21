/*
 * Copyright (C) 2013 The Android Open Source Project
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

#define LOG_TAG "engjni"
#include "utils/Log.h"

#include <stdint.h>
#include <jni.h>

#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>

#define ROOT_MAGIC 0x524F4F54 //"ROOT"
#define ROOT_OFFSET 512


extern "C" {
#include "sprd_efuse_hw.h"
}


typedef struct {
  uint32_t magic;
  uint32_t root_flag;
} root_stat_t;

static jint get_rootflag(JNIEnv* env, jobject thiz) {

    char block_device[100];
    //strcpy(block_device, "/dev/block/platform/sdio_emmc/by-name/miscdata");
    strcpy(block_device, "/sys/root_recorder/rootrecorder");

    root_stat_t stat;
    FILE *device;
    int retval = 0;

    device = fopen(block_device, "r");
    if (!device) {
//        ALOGE("[root_recorder] Could not open block device %s (%s).\n", block_device, strerror(errno));
        goto out;
    }

    //if (fseek(device, ROOT_OFFSET, SEEK_SET) < 0) {
        //ALOGE("[root_recorder] Could not seek to start of ROOT FLAG metadata block.\n");
        //goto out;
    //}
    if (!fread(&stat, sizeof(root_stat_t), 1, device)) {
        ALOGE("[root_recorder] Couldn't read magic number!\n");
        goto out;
    }

    ALOGD("[root_recorder] magic=%d\n",stat.magic);
    ALOGD("[root_recorder] rootflag=%d\n",stat.root_flag);

    if(stat.magic == ROOT_MAGIC) {
        ALOGE("[root_recorder] sprd magic verify pass.\n");
        retval = stat.root_flag;
    } else {
        ALOGE("[root_recorder] sprd magic verify failed.\n");
    }

out:
    if (device)
        fclose(device);
    return retval;

}


static jboolean Hardware_hashValueWrited(JNIEnv* env, jobject thiz) {      
if (efuse_is_hash_write() == 1) {
ALOGD("hash value has writed");
return true;
}
ALOGD("hash value has not writed");
return false;
}

static const char *hardWareClassPathName =
        "com.sprd.engineermode.hardware/HardWareFragment";
        
static JNINativeMethod getMethods[] = {
		{"hashValueWrited", "()Z", (void*) Hardware_hashValueWrited },
		{"get_rootflag", "()I", (void*)get_rootflag}
};
        
        
static int registerNativeMethods(JNIEnv* env, const char* className,
        JNINativeMethod* gMethods, int numMethods) {
    jclass clazz;
    clazz = env->FindClass(className);
    if (clazz == NULL) {
        ALOGE("Native registration unable to find class '%s'", className);
        return JNI_FALSE;
    }
    if (env->RegisterNatives(clazz, gMethods, numMethods) < 0) {
        ALOGE("RegisterNatives failed for '%s'", className);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}
        
jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    //use JNI1.6
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        ALOGE("Error: GetEnv failed in JNI_OnLoad");
        return -1;
    }
    if (!registerNativeMethods(env, hardWareClassPathName, getMethods,
            sizeof(getMethods) / sizeof(getMethods[0]))) {
        ALOGE("Error: could not register native methods for HardwareFragment");
        return -1;
    }
      return JNI_VERSION_1_6;
}
