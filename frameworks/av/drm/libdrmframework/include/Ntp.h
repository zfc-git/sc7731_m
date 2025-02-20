#ifndef _H_NTPCLIENTFUNC
#define _H_NTPCLIENTFUNC

#ifdef __LP64__
typedef time_t time64_t;
#else
#include <time64.h>

#endif

/******************************************************************************************
 LI 2(bit) | VN 3(bit) | Mode 3(bit) |Streatum 8(bit) | Poll 8(bit) | Precision 8(bit)
 __________________________________________________________________________________________
 Root Delay 32(bit)
 __________________________________________________________________________________________
 Root Dispersion 32(bit)
 _________________________________________________________________________________________
 Reference Identifier 32(bit)
 ______________________________________________________________________________________________
 Reference timestamp 64(bit)
 ___________________________________________________________________________________________
 Originate  Timestamp 64(bit)
 ___________________________________________________________________________________________
 Receive timestamp  64(bit)
 ___________________________________________________________________________________________
 Transmit  Timestamp 64(bit)
 _____________________________________________________________________________________________
 Key Identifier (optional) 32(bit)
 _____________________________________________________________________________________________
 Message digest(otptional) 128(ibt)
 ___________________________________________________________________________________________
。
 ********************************************************************************************/

#define NTP_SERVER0			"0.asia.pool.ntp.org"
#define NTP_SERVER1			"1.asia.pool.ntp.org"
#define NTP_SERVER2 		"2.asia.pool.ntp.org"
#define NTP_SERVER3 		"3.asia.pool.ntp.org"
#define NTP_SERVER4			"0.cn.pool.ntp.org"
#define NTP_SERVER5			"0.hk.pool.ntp.org"
#define NTP_SERVER6			"3.tw.pool.ntp.org"
#define NTP_SERVER7			"0.jp.pool.ntp.org"
#define NTP_SERVER8			"1.jp.pool.ntp.org"
#define NTP_SERVER9			"2.jp.pool.ntp.org"
#define NTP_SERVER10		"3.jp.pool.ntp.org"
#define NTP_SERVER11		"0.kr.pool.ntp.org"
#define NTP_SERVER12		"0.us.pool.ntp.org"
#define NTP_SERVER13		"1.us.pool.ntp.org"
#define NTP_SERVER14		"2.us.pool.ntp.org"
#define NTP_SERVER15		"3.us.pool.ntp.org"
	

#define NTP_PORT              123
//rfc1305 defined from 1900 so also  2208988800 (1900 - 1970 ) seconds left
//timeval.tv_sec + JAN_1970 = timestamp.coarse
#define JAN_1970       0x83aa7e80
//timeval.tv_usec=>timestamp.fine
#define NTPFRAC(x) (4294 * (x) + ((1981 * (x))>>11))
//timeval.tv_usec<=timestamp.fine
#define USEC(x) (((x) >> 12) - 759 * ((((x) >> 10) + 32768) >> 16))
#define ANDROID_ALARM_WAIT_CHANGE           _IOW('a', 10, int)
#define NTP_CONNECT_MAX_TIME 	30
#define NTP_RECV_TIMEOUT		10
namespace android {
class NTP {
private:
    typedef struct NtpTime {
        unsigned int coarse;
        unsigned int fine;
    } NTPTIME;

    typedef struct ntpheader {
        union {
            struct {
                char local_precision;
                char Poll;
                unsigned char stratum;
                unsigned char Mode :3;
                unsigned char VN :3;
                unsigned char LI :2;
            };
            unsigned int headData;
        };
    } NTPHEADER;

    typedef struct NtpPacked {
        NTPHEADER header;

        unsigned int root_delay;
        unsigned int root_dispersion;
        unsigned int refid;
        NTPTIME reftime;
        NTPTIME orgtime;
        NTPTIME recvtime;
        NTPTIME trantime;
    } NTPPACKED, *PNTPPACKED;

    static int createNTPClientSockfd();
    static int connectNTPServer(int sockfd, char * serverAddr, int serverPort,struct sockaddr_in * ServerSocket_in);
    static int recvNTPPacked(int sockfd, PNTPPACKED pSynNtpPacked,struct sockaddr_in * ServerSocket_in);
    static void sendQueryTimePacked(int sockfd);
    static void pollingNTPTime();
    static void pollingAlarm();
    static void saveDelta();
    static void loadDelta();
    
    NTP() {}
public:
    static void startPolling();
    static time64_t getStandardTime();
};
};
#endif
