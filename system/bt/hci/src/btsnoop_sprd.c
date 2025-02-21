/******************************************************************************
 *
 *  Copyright (C) 2014 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/
#include "bt_target.h"
#if (defined(SPRD_FEATURE_SLOG) && SPRD_FEATURE_SLOG == TRUE)
#define LOG_TAG "bt_snoop_sprd"

#include <arpa/inet.h>
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <netinet/in.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <unistd.h>

#include "hci/include/btsnoop.h"
#include "hci/include/btsnoop_mem.h"
#include "bt_types.h"
#include "hci_layer.h"
#include "osi/include/log.h"
#include "stack_config.h"

#include <unistd.h>
#include <pthread.h>
#include <sys/inotify.h>
#include <cutils/sockets.h>
#include <sys/un.h>
#include <sys/poll.h>

#ifndef SNOOP_DBG
#define SNOOP_DBG TRUE
#endif

#if (SNOOP_DBG == TRUE)
#define SNOOPD(param, ...) ALOGD("%s "param, __FUNCTION__, ## __VA_ARGS__)
#else
#define SNOOPD(param, ...) {}
#endif

#ifdef SNOOPD_V
#define SNOOPV(param, ...) ALOGD("%s " param, __FUNCTION__, ## __VA_ARGS__)
#else
#define SNOOPV(param, ...) {}
#endif

#define SNOOPE(param, ...) ALOGE("%s "param, __FUNCTION__, ## __VA_ARGS__)


/* macro for parsing config file */
#define BTE_STACK_CONF_FILE "/data/misc/bluedroid/bt_stack.conf"
#define CONF_MAX_LINE_LEN 255
#define CONF_COMMENT '#'
#define CONF_DELIMITERS " =\n\r\t"
#define CONF_VALUES_DELIMITERS "=\n\r\t#"

#define EFD_SEMAPHORE (1 << 0)
#define EVENT_SIZE (sizeof(struct inotify_event))
#define EVENT_BUF_LEN (1024 * (EVENT_SIZE + 16))

/* macro for socket */
#define LOG_SOCKET "bt_snoop_slog_socket"
#define HCI_EDR3_DH5_PACKET_SIZE    1021
#define BTSNOOP_HEAD_SIZE  25
#define HCI_PIIL_SVAE_BUF_SIZE 5
#define SLOG_STREAM_OUTPUT_BUFFER_SZ      ((HCI_EDR3_DH5_PACKET_SIZE + BTSNOOP_HEAD_SIZE) * 5)
#define HCI_POLL_SIZE (HCI_EDR3_DH5_PACKET_SIZE + BTSNOOP_HEAD_SIZE + HCI_PIIL_SVAE_BUF_SIZE)
#define CTRL_CHAN_RETRY_COUNT 3

#define CASE_RETURN_STR(const) case const: return #const;

typedef enum {
    SNOOP_FILE,
    SNOOP_SOCKET,
    SNOOP_UNKNOW
} LOG_TYPE_T;

typedef enum {
    CMD = 1,
    ACL,
    SCO,
    EVE
} PACKET_TYPE;

typedef enum {
    kCommandPacket = 1,
    kAclPacket = 2,
    kScoPacket = 3,
    kEventPacket = 4
} packet_type_t;

// Epoch in microseconds since 01/01/0000.
static const uint64_t BTSNOOP_EPOCH_DELTA = 0x00dcddb30f2f8000ULL;

static int btsnoop_fd = INVALID_FD;
static LOG_TYPE_T btsnoop_type = SNOOP_UNKNOW;

static volatile unsigned char task_running = 0;
static pthread_t btsnoop_tid = (pthread_t)(-1);
static int config_fd = INVALID_FD;
static bool hci_logging_enabled = 0;
static char hci_logfile[256] = {0};
static struct timeval tv;
static unsigned char hci_pool[HCI_POLL_SIZE];


// TODO(zachoverflow): merge btsnoop and btsnoop_net together
void btsnoop_net_open();
void btsnoop_net_close();
void btsnoop_net_write(const void *data, size_t length);
static uint64_t btsnoop_timestamp(void);

static void btsnoop_write_packet(packet_type_t type, const uint8_t *packet, bool is_received);

// Module lifecycle functions

static uint64_t snoop_timestamp = 0;
static int semaphore_fd = INVALID_FD;

static int is_skt_alive(int socket_fd) {

    struct pollfd pfd;
    int ret = 0;
    pfd.fd = socket_fd;
    pfd.events = POLLOUT | POLLHUP;
    SNOOPD();

    ret = poll(&pfd, 1, 500);

    if (ret == 0) {
        SNOOPE("poll timeout (%s)", strerror(errno));
        goto error;
    }

    if (ret < 0) {
        SNOOPE("poll error (%s)", strerror(errno));
        goto error;
    }

    if (pfd.revents & (POLLHUP | POLLNVAL)) {
        SNOOPE("poll socket detached remotely");
        goto error;
    }

    if (pfd.revents & POLLOUT) {
        SNOOPE("poll socket OK");
    }

    return 0;

error:

    return -1;
}

static int skt_connect(char *path, size_t buffer_sz)
{
    int ret;
    int skt_fd;
    struct sockaddr_un remote;
    int len;

    SNOOPV("connect to %s (sz %zu)", path, buffer_sz);

    if((skt_fd = socket(AF_LOCAL, SOCK_STREAM, 0)) == -1)
    {
        SNOOPE("failed to socket (%s)", strerror(errno));
        return -1;
    }

    if(socket_local_client_connect(skt_fd, path,
            ANDROID_SOCKET_NAMESPACE_ABSTRACT, SOCK_STREAM) < 0)
    {
        SNOOPE("failed to connect (%s)", strerror(errno));
        close(skt_fd);
        return -1;
    }

    len = buffer_sz;
    ret = setsockopt(skt_fd, SOL_SOCKET, SO_SNDBUF, (char*)&len, (int)sizeof(len));

    /* only issue warning if failed */
    if (ret < 0)
        SNOOPE("setsockopt failed (%s)", strerror(errno));

    ret = setsockopt(skt_fd, SOL_SOCKET, SO_RCVBUF, (char*)&len, (int)sizeof(len));

    /* only issue warning if failed */
    if (ret < 0)
        SNOOPE("setsockopt failed (%s)", strerror(errno));

    SNOOPV("connected to stack fd = %d", skt_fd);

    return skt_fd;
}

static int update_btsnoop_log_state(char* file_name)
{
    char line[CONF_MAX_LINE_LEN+1]; /* add 1 for \0 char */
    FILE    *p_file;
    char    *p_name;
    char    *p_value;
    if ((p_file = fopen(file_name, "r")) == NULL) {
        return -1;
    }

    while (fgets(line, CONF_MAX_LINE_LEN+1, p_file) != NULL) {
        if (line[0] == CONF_COMMENT)
            continue;
            p_name = strtok(line, CONF_DELIMITERS);

            if (NULL == p_name)
            {
                continue;
            }

            p_value = strtok(NULL, CONF_VALUES_DELIMITERS);

            if (NULL == p_value)
            {
                SNOOPE("vnd_load_conf: missing value for name: %s", p_name);
                continue;
            }
            //SNOOPD("Got Name: %s, Value: %s", p_name, p_value);
            if (strcmp(p_name, "BtSnoopLogOutput") == 0) {
                if (strcmp(p_value, "true") == 0) {
                    hci_logging_enabled = 1;
                } else {
                    hci_logging_enabled = 0;
                }
            }
            if (strcmp(p_name, "BtSnoopFileName") == 0) {
                memcpy(hci_logfile, p_value, strlen(p_value));
                hci_logfile[strlen(p_value)] = 0;
            }
    }
    SNOOPD("hci_logging_enabled: %d, hci_logfile: %s", hci_logging_enabled, hci_logfile);
    return 0;
}

static void btsnoop_open(const char *p_path) {
    assert(p_path != NULL);
    assert(*p_path != '\0');

    SNOOPD("path: %s", p_path);

    btsnoop_net_open();

    if (btsnoop_fd != INVALID_FD) {
        SNOOPD("btsnoop log file is already open.");
        return;
    }

    if (0 == strcmp(p_path, LOG_SOCKET)) {
        int i;
        SNOOPD("save in socket");
        btsnoop_type = SNOOP_SOCKET;
        for (i =0; i < CTRL_CHAN_RETRY_COUNT; i++) {
            btsnoop_fd = skt_connect(LOG_SOCKET, SLOG_STREAM_OUTPUT_BUFFER_SZ);
            if (btsnoop_fd > 0) {
                SNOOPD("%s connected", LOG_SOCKET);
                break;
            } else {
                usleep(150 * 1000);
                SNOOPD("retry to: %s", LOG_SOCKET);
            }
        }
        if (btsnoop_fd < 0) {
            SNOOPE("%s connect failed", LOG_SOCKET);
            hci_logging_enabled = FALSE;
            return;
        }
    } else {
        SNOOPD("save in file");
        btsnoop_type = SNOOP_FILE;
        /* save in file */
        char logfile[255]={0};
        time_t now;
        struct tm *timenow;
        time(&now);
        timenow = localtime(&now);

        sprintf(logfile, "%s/btsnoop_%04d-%02d-%02d_%02d-%02d-%02d.log",
        p_path, timenow->tm_year + 1900, timenow->tm_mon + 1, timenow->tm_mday,
        timenow->tm_hour, timenow->tm_min, timenow->tm_sec);

        btsnoop_fd = open(logfile,
                        O_WRONLY | O_CREAT | O_TRUNC,
                        S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH);

        if (btsnoop_fd == INVALID_FD) {
            SNOOPE("unable to open '%s': %s", logfile, strerror(errno));
            return;
        }
        write(btsnoop_fd, "btsnoop\0\0\0\0\1\0\0\x3\xea", 16);
    }
}

static void btsnoop_close(void) {
    SNOOPD();
    if (btsnoop_fd != INVALID_FD) {
        close(btsnoop_fd);
        btsnoop_fd = INVALID_FD;
    }
    btsnoop_net_close();
}

static void btsnoop_cleanup(int sig)
{
    int ret;
    SNOOPD("receive signal %d", sig);
    btsnoop_close();
    if (config_fd != INVALID_FD) {
        close(config_fd);
        config_fd = INVALID_FD;
    }
    if (semaphore_fd != INVALID_FD) {
        ret = eventfd_write(semaphore_fd, 1ULL);
        if (ret < 0) {
            SNOOPE("%s unable to post to semaphore: %s", __func__, strerror(errno));
        }
    }
    task_running = 0;
    pthread_exit(0);
}

static void *btsnoop_task(void *arg)
{
    int wd;
    int length;
    char buffer[EVENT_BUF_LEN];
    struct inotify_event *event;
    struct sigaction actions;
    BOOLEAN old_BtSnoopLogOutput;
    char old_hci_logfile[256] = {0};

    if (task_running == 0) {
        task_running = 1;
        SNOOPD(" start");
    } else {
        SNOOPD(" already start");
        goto th_exit;
    }

    /* set detach, when thread exit, resources will automatically released */
    if(pthread_detach(pthread_self()) != 0) {
        SNOOPE("pthread_detach fail");
    }

    if((config_fd = inotify_init()) < 0){
        SNOOPE("inotify_init fail (%d)", config_fd);
        goto th_exit;
    }

    SNOOPD("config_fd = %d, file: %s", config_fd, BTE_STACK_CONF_FILE);

    wd = inotify_add_watch(config_fd, BTE_STACK_CONF_FILE, IN_MODIFY);
    if(wd < 0){
        SNOOPE("inotify_add_watch fail, bt_slog_task thread exit");
        close(config_fd);
        goto th_exit;
    }

    memset(&actions, 0, sizeof(actions));
    sigemptyset(&actions.sa_mask);
    actions.sa_flags = 0;
    actions.sa_handler = btsnoop_cleanup;
    sigaction(SIGUSR2,&actions,NULL);

    while (task_running)
    {
        SNOOPD("watching");
        length = read(config_fd, buffer, EVENT_BUF_LEN);//block if not modify

        SNOOPD("file inotify");
        if(length < 0){
            SNOOPD("read length: %d", length);
            continue;
        }

        event = (struct inotify_event *)buffer;

        if(event->mask & IN_MODIFY) {

            /* save old state before reload config file */
            old_BtSnoopLogOutput = hci_logging_enabled;
            strcpy(old_hci_logfile, hci_logfile);

            /* reload config */
            update_btsnoop_log_state(BTE_STACK_CONF_FILE);

            SNOOPD("BtSnoopLogOutput : %d\n",hci_logging_enabled);
            SNOOPD("BtSnoopFileName: %s\n",hci_logfile);

            /* if the modify is not our care, ignore it */
            if(old_BtSnoopLogOutput == hci_logging_enabled && !strcmp(old_hci_logfile, hci_logfile)){
                if (btsnoop_type == SNOOP_SOCKET && is_skt_alive(btsnoop_fd)) {
                    SNOOPD("snoop socket miss, restart it\n");
                } else {
                    SNOOPD("No Change, ignore.\n");
                    continue;
                }
            }

            if(hci_logging_enabled == TRUE){
                SNOOPD("enable hci slog");
                btsnoop_close();
                btsnoop_open(hci_logfile);
            }else{
                SNOOPD("disable hci slog");
                btsnoop_close();
            }
        }
    }

th_exit:
    btsnoop_cleanup(INVALID_FD);
    SNOOPD("thread exit");
    return(0);
}

static future_t *start_up(void) {
    SNOOPD();
    if (semaphore_fd != INVALID_FD) {
        SNOOPE("warning semaphore_fd: %d", semaphore_fd);
        close(semaphore_fd);
    }
    semaphore_fd = eventfd(0, EFD_SEMAPHORE);
    if (semaphore_fd == INVALID_FD) {
        SNOOPE("error");
    }
    update_btsnoop_log_state(BTE_STACK_CONF_FILE);
    if (hci_logging_enabled) {
        btsnoop_open(hci_logfile);
    }
    if (pthread_create(&btsnoop_tid, NULL, btsnoop_task, NULL) < 0) {
        SNOOPE("start failed");
    }
    return NULL;
}

static future_t *shut_down(void) {
    int ret;
    uint64_t value;
    SNOOPD("enter");
    ret = pthread_kill(btsnoop_tid, SIGUSR2);
    if (ret < 0) {
        SNOOPE(" error");
        return;
    }
    assert(semaphore_fd != INVALID_FD);
    if (eventfd_read(semaphore_fd, &value) == -1) {
        SNOOPE("%s unable to wait on semaphore: %s", __func__, strerror(errno));
    }
    close(semaphore_fd);
    semaphore_fd = INVALID_FD;
    SNOOPD("out");
    return NULL;
}

const module_t btsnoop_sprd_module = {
    .name = BTSNOOP_SPRD_MODULE,
    .init = NULL,
    .start_up = start_up,
    .shut_down = shut_down,
    .clean_up = NULL
};

// Interface functions

static void dump_hex(unsigned char *src, int len) {
    int buf_size = len * 5 +1;
    char *mem = malloc(buf_size);
    memset(mem, 0, buf_size);
    char temp[6] = {0};
    int i;
    for (i = 25; i < len; i++) {
        memset(temp, 0, sizeof(temp));
        sprintf(temp, "0x%02X ", src[i]);
        strcat(mem, temp);
    }
    ALOGD("%s", mem);
    free(mem);
    mem = NULL;
}

static void capture(const BT_HDR *buffer, bool is_received) {
    const uint8_t *p = buffer->data + buffer->offset;

    if (btsnoop_fd == INVALID_FD)
        return;

    switch (buffer->event & MSG_EVT_MASK) {
        case MSG_HC_TO_STACK_HCI_EVT:
            btsnoop_write_packet(kEventPacket, p, false);
            break;
        case MSG_HC_TO_STACK_HCI_ACL:
        case MSG_STACK_TO_HC_HCI_ACL:
            btsnoop_write_packet(kAclPacket, p, is_received);
            break;
        case MSG_HC_TO_STACK_HCI_SCO:
        case MSG_STACK_TO_HC_HCI_SCO:
            btsnoop_write_packet(kScoPacket, p, is_received);
            break;
        case MSG_STACK_TO_HC_HCI_CMD:
            btsnoop_write_packet(kCommandPacket, p, true);
            break;
    }

    ALOGD("record diff: %llu", (btsnoop_timestamp() - snoop_timestamp));
}

static const btsnoop_t interface = {
  NULL,
  capture
};

const btsnoop_t *btsnoop_sprd_get_interface() {
  return &interface;
}

// Internal functions
static void btsnoop_write(const void *data, size_t length) {
    if (btsnoop_fd != INVALID_FD)
        write(btsnoop_fd, data, length);

    btsnoop_net_write(data, length);
}

static const char* dump_packet_type(PACKET_TYPE type) {
    switch(type) {
        CASE_RETURN_STR(CMD)
        CASE_RETURN_STR(ACL)
        CASE_RETURN_STR(SCO)
        CASE_RETURN_STR(EVE)
        default:
            return "UNK";
    }
}

static inline void dump_packet(const char* type) {
    struct tm *packet_time = gmtime(&tv.tv_sec);
    ALOGD("%02d:%02d:%02d:%06ld %s",
        packet_time->tm_hour, packet_time->tm_min, packet_time->tm_sec, tv.tv_usec, type);
}

static uint64_t btsnoop_timestamp(void) {
    gettimeofday(&tv, NULL);

    // Timestamp is in microseconds.
    uint64_t timestamp = tv.tv_sec * 1000 * 1000LL;
    timestamp += tv.tv_usec;
    timestamp += BTSNOOP_EPOCH_DELTA;
    return timestamp;
}

static void btsnoop_write_packet(packet_type_t type, const uint8_t *packet, bool is_received) {
    int length_he = 0;
    int length;
    int flags;
    int drops = 0;
    switch (type) {
        case kCommandPacket:
            length_he = packet[2] + 4;
            flags = 2;
            break;
        case kAclPacket:
            length_he = (packet[3] << 8) + packet[2] + 5;
            flags = is_received;
            break;
        case kScoPacket:
            length_he = packet[2] + 4;
            flags = is_received;
            break;
        case kEventPacket:
            length_he = packet[1] + 3;
            flags = 3;
        break;
    }

    snoop_timestamp = btsnoop_timestamp();
    uint32_t time_hi = snoop_timestamp >> 32;
    uint32_t time_lo = snoop_timestamp & 0xFFFFFFFF;

    length = htonl(length_he);
    flags = htonl(flags);
    drops = htonl(drops);
    time_hi = htonl(time_hi);
    time_lo = htonl(time_lo);

    dump_packet(dump_packet_type(type));

    if (btsnoop_type == SNOOP_FILE) {
        btsnoop_write(&length, 4);
        btsnoop_write(&length, 4);
        btsnoop_write(&flags, 4);
        btsnoop_write(&drops, 4);
        btsnoop_write(&time_hi, 4);
        btsnoop_write(&time_lo, 4);
        btsnoop_write(&type, 1);
        btsnoop_write(packet, length_he - 1);
    } else if (btsnoop_type == SNOOP_SOCKET) {
        memset(hci_pool, 0, sizeof(hci_pool));
        if (BTSNOOP_HEAD_SIZE + length_he - 1 > HCI_POLL_SIZE) {
            SNOOPE("warning out of buffer");
        }
        memcpy(hci_pool, &length, 4);
        memcpy(hci_pool + 4, &length, 4);
        memcpy(hci_pool + 8, &flags, 4);
        memcpy(hci_pool + 12, &drops, 4);
        memcpy(hci_pool + 16, &time_hi, 4);
        memcpy(hci_pool + 20, &time_lo, 4);
        memcpy(hci_pool + 24, &type, 1);
        memcpy(hci_pool + 25, packet, length_he - 1);
#ifdef SNOOPD_V
        dump_hex(hci_pool, (BTSNOOP_HEAD_SIZE + length_he - 1));
#endif

        if (send(btsnoop_fd, hci_pool, (BTSNOOP_HEAD_SIZE + length_he - 1), 0) < 0) {
            SNOOPE(" error");
            close(btsnoop_fd);
            btsnoop_fd = -1;
            hci_logging_enabled = FALSE;
        }

        btsnoop_net_write(&length, 4);
        btsnoop_net_write(&length, 4);
        btsnoop_net_write(&flags, 4);
        btsnoop_net_write(&drops, 4);
        btsnoop_net_write(&time_hi, 4);
        btsnoop_net_write(&time_lo, 4);
        btsnoop_net_write(&type, 1);
        btsnoop_net_write(packet, length_he - 1);

    }
}

#endif
