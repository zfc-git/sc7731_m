#include <arpa/inet.h>
#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <stdio.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <time.h>
#include <utils/Log.h>
#include <cutils/sockets.h>
#include <pthread.h>
#include "Ntp.h"

namespace android {
    pthread_mutex_t _mutex = PTHREAD_MUTEX_INITIALIZER;
    time64_t _delta = 0;
	char* server_list[] = {NTP_SERVER0, NTP_SERVER1, NTP_SERVER2, NTP_SERVER3,
						   NTP_SERVER4, NTP_SERVER5, NTP_SERVER6, NTP_SERVER7,
						   NTP_SERVER8, NTP_SERVER9, NTP_SERVER10, NTP_SERVER11,
						   NTP_SERVER12, NTP_SERVER13, NTP_SERVER14, NTP_SERVER15};
	
    int NTP::createNTPClientSockfd() {
        int sockfd;
        int addr_len;
        struct sockaddr_in addr_src;
        int ret;

        addr_len = sizeof(struct sockaddr_in);
        memset(&addr_src, 0, addr_len);
        addr_src.sin_family = AF_INET;
        addr_src.sin_addr.s_addr = htonl(INADDR_ANY);
        addr_src.sin_port = htons(0);
        /* create socket. */
        if (-1 == (sockfd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP))) {
            ALOGE("drm_ntp: create socket error! %s", strerror(errno));
            return -1;
        }
        ALOGE("drm_ntp: CreateNtpClientSockfd sockfd=%d\n", sockfd);
#if 0		
        /* bind local address. */
        ret = bind(sockfd, (struct sockaddr*) &addr_src, addr_len);
        if (-1 == ret) {
            close(sockfd);
            return -1;
        }
#endif		
        return sockfd;
    }

    int NTP::connectNTPServer(int sockfd, char * serverAddr, int serverPort, struct sockaddr_in * ServerSocket_in) {

		struct addrinfo		hints;
		struct addrinfo*	result = 0;
		struct addrinfo*	iter = 0;
        int ret;
		bzero(&hints, sizeof(struct addrinfo));
		hints.ai_family = AF_UNSPEC;
		hints.ai_socktype = SOCK_STREAM;
		hints.ai_flags = AI_CANONNAME;
		hints.ai_protocol = 0;
		ret = getaddrinfo((const char*)serverAddr, 0, &hints, &result);
		if (ret != 0) {
			ALOGE("drm_ntp: get hostname %s address info failed! %s", serverAddr, gai_strerror(ret));
			return -1;
		}

		char host[1025] = "";
		for (iter = result; iter != 0; iter = iter->ai_next) {
			ret = getnameinfo(result->ai_addr, result->ai_addrlen, host, sizeof(host), 0, 0, NI_NUMERICHOST);
			if (ret != 0) {
				ALOGE("drm_ntp: get hostname %s name info failed! %s", serverAddr, gai_strerror(ret));
				continue;
			} else {
				ALOGE("drm_ntp: hostname %s -> ip %s", serverAddr, host);
				struct sockaddr_in addr_dst;		
				int addr_len;
				addr_len = sizeof(struct sockaddr_in);
				memset(&addr_dst, 0, addr_len);
				addr_dst.sin_family = AF_INET;
				addr_dst.sin_addr.s_addr = inet_addr(host);
				addr_dst.sin_port = htons(serverPort);
				memcpy(ServerSocket_in, &addr_dst, sizeof(struct sockaddr_in));
				
				/* connect to ntp server. */
				ALOGE("drm_ntp: try to connect ntp server %s", serverAddr);
				ret = connect(sockfd, (struct sockaddr*) &addr_dst, addr_len);
				if (-1 == ret) {
		            ALOGE("drm_ntp: connect to ntp server %s failed, %s", serverAddr, strerror(errno));
					continue;
				} else {
					ALOGE("drm_ntp: ConnectNtpServer sucessful!\n");
					break;
				}
			}
		}

		if (result) freeaddrinfo(result);
        return sockfd;
    }

    void NTP::sendQueryTimePacked(int sockfd) {

        NTPPACKED SynNtpPacked;
        struct timeval now;
		time_t timer;
        memset(&SynNtpPacked, 0, sizeof(SynNtpPacked));
		
        SynNtpPacked.header.local_precision = -6;
        SynNtpPacked.header.Poll = 4;
        SynNtpPacked.header.stratum = 0;
        SynNtpPacked.header.Mode = 3;
        SynNtpPacked.header.VN = 3;
        SynNtpPacked.header.LI = 0;
		
        SynNtpPacked.root_delay = 1 << 16; /* Root Delay (seconds) */
        SynNtpPacked.root_dispersion = 1 << 16; /* Root Dispersion (seconds) */

//        SynNtpPacked.header.headData = htonl(SynNtpPacked.header.headData);
		SynNtpPacked.header.headData = htonl((SynNtpPacked.header.LI << 30) | (SynNtpPacked.header.VN << 27) |
									   		(SynNtpPacked.header.Mode << 24)| (SynNtpPacked.header.stratum << 16) |
									   		(SynNtpPacked.header.Poll << 8) | (SynNtpPacked.header.local_precision & 0xff));	
        SynNtpPacked.root_delay = htonl(SynNtpPacked.root_dispersion);
        SynNtpPacked.root_dispersion = htonl(SynNtpPacked.root_dispersion);

        //gettimeofday(&now, NULL); /* get current local_time*/

        //SynNtpPacked.trantime.fine = htonl(NTPFRAC(now.tv_usec)); /* Transmit Timestamp fine   */
		long tmp = 0;
		time(&timer);
		SynNtpPacked.trantime.coarse = htonl(JAN_1970 + (long)timer);
		SynNtpPacked.trantime.fine = htonl((long)NTPFRAC(timer));
		
        send(sockfd, &SynNtpPacked, sizeof(SynNtpPacked), 0);
    }

    int NTP::recvNTPPacked(int sockfd, PNTPPACKED pSynNtpPacked, struct sockaddr_in * ServerSocket_in) {

        int receivebytes = -1;
        socklen_t addr_len = sizeof(struct sockaddr_in);
		fd_set sockset;

		FD_ZERO(&sockset);
		FD_SET(sockfd, &sockset);
		struct timeval blocktime = {NTP_RECV_TIMEOUT, 0};

		/* recv ntp server's response. */
		if (select(sockfd+1, &sockset, 0, 0, &blocktime) > 0) {
			receivebytes = recvfrom(sockfd, pSynNtpPacked, sizeof(NTPPACKED), 0,(struct sockaddr *) ServerSocket_in, &addr_len);
			if (-1 == receivebytes) {
				ALOGE("drm_ntp: recvfrom error! %s", strerror(errno));
				return -1;
			} else {
				ALOGE("drm_ntp: recvfrom receivebytes=%d",receivebytes);
			}
		} else {
			ALOGE("drm_ntp: recvfrom timeout! %s", strerror(errno));
		}

        return receivebytes;
    }

void NTP::pollingNTPTime() {
	int lsocket = android_get_control_socket("drm_connectivity");
	if (lsocket < 0) {
	    ALOGE("drm_ntp: Failed to get socket from environment!");
	    return;
	}
	if (listen(lsocket, 5)) {
	    ALOGE("drm_ntp: Listen on socket failed: %s\n", strerror(errno));
	    return;
	}
	fcntl(lsocket, F_SETFD, FD_CLOEXEC);

	struct sockaddr addr;
	socklen_t alen = sizeof(addr);
	int s = -1;
	ALOGE("drm_ntp: accepting on socket");
	while (1) {
		s = accept(lsocket, &addr, &alen);
		if (s < 0) {
			ALOGE("drm_ntp:Accept failed: %s\n", strerror(errno));
//			return;
		} else {
			break;
		}
	}

	fcntl(s, F_SETFD, FD_CLOEXEC);

	char buffer[1024];
	int i = 0;
	int list_num = sizeof(server_list)/sizeof(server_list[0]);
	int ret = -1;
	int sockfd = -1;
	pthread_t tid = 0;
	do {
start:		
		if (i >= NTP_CONNECT_MAX_TIME) goto retry;
		
	    ALOGE("drm_ntp:connecting to ntp server");
	    sockfd = createNTPClientSockfd();
	    if (sockfd == -1) {
			ALOGE("drm_ntp: create socket failed");
			goto start;
	    }
	    struct sockaddr_in ServerSocketn;
	    ret= connectNTPServer(sockfd, (char *)server_list[i++%list_num], NTP_PORT, &ServerSocketn);
	    if (ret == -1) {
			ALOGE("drm_ntp: sleep 2s and reconnect...");
            close(sockfd);
			sleep(2);
            goto start;
	    }

		/* send ntp protocol packet. */
		sendQueryTimePacked(sockfd);

        NTPPACKED syn_ntp_packed;
        ret = recvNTPPacked(sockfd,&syn_ntp_packed,&ServerSocketn);
        if (ret == -1) {
	        ALOGE("drm_ntp: recv from ntp server failed");			
			close(sockfd);
	        goto start;
        }
	
        NTPTIME trantime;
        trantime.coarse = ntohl(syn_ntp_packed.trantime.coarse) - JAN_1970;
        pthread_mutex_lock(&_mutex);
        _delta = trantime.coarse - time(NULL);
        pthread_mutex_unlock(&_mutex);
			
	    saveDelta();
	    
	    pthread_create(&tid,NULL,(void*(*)(void*))&NTP::pollingAlarm,NULL);

	    ALOGE("drm_ntp: pollingNTPTime: set delta to %d", (int)_delta);

	    close(sockfd);
	    close(s);
		ALOGE("drm_ntp: quit polling NTP time!");
	    return;
retry:
		ALOGE("drm_ntp: query ntp failed, watching network connection to retry!");
		i = 0;
	} while (read(s,buffer,1024));
}

    void NTP::pollingAlarm(){
	time64_t tmp = 0;
        int fd = open("/dev/alarm", O_RDONLY);
	while(1){
	    int ret_time = ioctl(fd, ANDROID_ALARM_WAIT_CHANGE, &tmp);
	    if (ret_time != -1) {
		pthread_mutex_lock(&_mutex);
		_delta -= tmp;
		pthread_mutex_unlock(&_mutex);
		ALOGE("drm_ntp: pollingAlarm: set delta to %d", (int)_delta);
		saveDelta();
		continue;
	    } 
	}
    }

    time64_t NTP::getStandardTime() {
	int delta = 0;
	pthread_mutex_lock(&_mutex);
	delta = _delta;
	pthread_mutex_unlock(&_mutex);
	return time(NULL)+delta;
    }

    void NTP::startPolling() {
	pthread_t tid;
	loadDelta();
	if (_delta == 1<<63) {	// not synced with ntp yet
	    ALOGE("drm_ntp: not synced with ntp yet");
	    pthread_create(&tid,NULL,(void*(*)(void*))(&NTP::pollingNTPTime),NULL);	    
	} else {
	    ALOGE("drm_ntp: synced with ntp");
	    pthread_create(&tid,NULL,(void*(*)(void*))&NTP::pollingAlarm,NULL);	    
	}
    }

    void NTP::saveDelta() {
	pthread_mutex_lock(&_mutex);
	time64_t tmp = _delta;
	pthread_mutex_unlock(&_mutex);
	
	int fd = open ("/data/drm/ntp",O_WRONLY|O_CREAT|O_TRUNC, 0777);
	if (fd == -1) {
	    return;
	}
	write(fd, &tmp, sizeof(tmp));
	int mark = 0xffffff;
	write(fd, &mark, 4);
	fsync(fd);
	close(fd);
    }

    void NTP::loadDelta() {
	int fd = open ("/data/drm/ntp",O_RDONLY);
	if (fd == -1) {
	    pthread_mutex_lock(&_mutex);
	    _delta = 1<<63;
	    pthread_mutex_unlock(&_mutex);
	    return;
	}
	time64_t tmp = 0;
	read(fd, &tmp, sizeof(time64_t));
	int flag = 0;
	read(fd, &flag, 4);
	pthread_mutex_lock(&_mutex);
	if (flag == 0xffffffff) {
	    _delta = tmp;
	} else {
	    _delta = 1<<63;
	}
	pthread_mutex_unlock(&_mutex);
	close(fd);
    }
}  // namespace android
