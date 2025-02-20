#define LOG_NDEBUG 0
#define LOG_TAG "VideoCallEngineClient"

#include "cutils/properties.h"
#include <utils/Log.h>
#include <binder/IPCThreadState.h>
#include <media/ICrypto.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MediaCodec.h>
#include <media/stagefright/NuMediaExtractor.h>
#include <media/stagefright/MediaMuxer.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/ALooper.h>
#include <media/stagefright/foundation/AString.h>
#include <media/stagefright/foundation/hexdump.h>
#include <media/stagefright/DataSource.h>
#include <media/stagefright/foundation/ABuffer.h>
#include <media/stagefright/CameraSource.h>
#include <media/AudioTrack.h>
#include <binder/ProcessState.h>
#include <media/stagefright/MediaDefs.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MetaData.h>
#include <OMX_Video.h>


#include <system/camera.h>
#include "../services/camera/libcameraservice/api1/Camera2Client.h"
#include <camera/Camera.h>
#include <camera/ICamera.h>
#include <camera/CameraParameters.h>

#include "VideoCallEngineAvcUtils.h"
#include "VideoCallEngineClient.h"


#define ROTATE_ENABLE  1
#define RECORD_TEST 0


#ifdef __cplusplus
extern "C" {
#endif

#define OSAL_PTHREADS
#define INCLUDE_VPR
#define INCLUDE_VIER

#include <vci.h>
#include <vier.h>

#ifdef __cplusplus
}
#endif

#if 0
#define VCI_EVENT_DESC_STRING_SZ (127)

typedef enum {
    VC_EVENT_NONE               = 0,
    VC_EVENT_INIT_COMPLETE      = 1,
    VC_EVENT_START_ENC          = 2,
    VC_EVENT_START_DEC          = 3,
    VC_EVENT_STOP_ENC           = 4,
    VC_EVENT_STOP_DEC           = 5,
    VC_EVENT_SHUTDOWN           = 6
} VC_Event;

typedef struct {
    uint8        *data_ptr;          /* data buffer */
    vint          length;            /* length of data */
    uint64        tsMs;              /* timestamp in milli-second */
    vint          flags;             /* SPS/PPS flags */
    uint8         rcsRtpExtnPayload; /* VCO - payload */
} VC_EncodedFrame;

vint VCI_init(void)
{
    return 0;
}

void VCI_shutdown(   void)
 {
 }

vint VCI_getEvent(
    VC_Event *event_ptr,
    char     *eventDesc_ptr,
    vint     *codecType_ptr,
    vint      timeout)
{
    return 0;
}

vint VCI_sendEncodedFrame(    VC_EncodedFrame *frame_ptr)
{
    return 0;
}

vint VCI_getEncodedFrame(    VC_EncodedFrame *frame_ptr)
{
    return 0;
}
OSAL_Status VIER_init(  void)
{
    return OSAL_SUCCESS;
}

OSAL_Status VIER_shutdown(    void)
{
    return OSAL_SUCCESS;
}
#endif



#define MAX_INPUT_BUFFERS  50

#if RECORD_TEST
static int counter = 0;
#endif

static const int64_t kTimeout = 500ll;
#if ROTATE_ENABLE
static const uint8 rotate_270 = 1;
static const uint8 rotate_180 = 2;
static const uint8 rotate_90 = 3;
static const uint8 flip_h = 4;
#endif

// For video encoded buffer timeStamp
static int64_t startSysTime = 0;

//For bug 473707, remove later
static int64_t pushFrame = 0;
static int64_t dropFrame = 0;
static int64_t cameraRelease = 0;
static int64_t encodeRelease = 0;

namespace android
{
    char value_dump[PROPERTY_VALUE_MAX];
    char *default_value = (char*)"false";
    bool video_dump_enable = false;

    enum VCE_ACTION_TYPE{
     VCE_ACTION_NOTIFY_CALLBACK
    };

    struct BufferInfo {
        size_t mIndex;
        size_t mOffset;
        size_t mSize;
        int64_t mPresentationTimeUs;
        uint32_t mFlags;
    };

    struct CodecState {
        sp<MediaCodec> mCodec;
        Vector<sp<ABuffer> > mInBuffers;
        Vector<sp<ABuffer> > mOutBuffers;
    };

    bool using_local_ps = false;
    uint8 sps_pps_saved = 0;
    uint8 *frame_sps = NULL;
    uint8 *frame_pps = NULL;
    unsigned int frame_sps_length = 0;
    unsigned int frame_pps_length = 0;

    VideoEncBitrateAdaption::VideoEncBitrateAdaption()
        : flag_upgrade(false),
        flag_tmmbr_ignore(false),
        max_bitrate(0),
        min_bitrate(0),
        bitrate_raise_cnt(0),
        prev_target_bitrate(-1),
        target_bitrate(-1),
        last_bitrate(-1),
        frame_rate_level(1),
        tm_tmmbr_ignore_start(0)
    {
        ALOGI("Construct VideoEncBitrateAdaption");
    }

    void VideoEncBitrateAdaption::init()
    {
        flag_upgrade = false;
        flag_tmmbr_ignore = false;
        max_bitrate = 0;
        min_bitrate = 0;
        bitrate_raise_cnt = 0;
        prev_target_bitrate = -1;
        target_bitrate = -1;
        last_bitrate = -1;
        frame_rate_level = 1;
        tm_tmmbr_ignore_start = 0;
        ALOGI("VideoEncBitrateAdaption::init()");
    }

    void VideoEncBitrateAdaption::setTargetBitRate(int bitrate)
    {
        target_bitrate = bitrate;
    }

    int VideoEncBitrateAdaption::getTargetBitRate()
    {
        return target_bitrate;
    }

    void VideoEncBitrateAdaption::setFrameRateLevel(int level)
    {
        frame_rate_level = level;
    }

    int VideoEncBitrateAdaption::getFrameRateLevel()
    {
        return frame_rate_level;
    }

    void VideoEncBitrateAdaption::setMaxBitRate(int val)
    {
        max_bitrate = val;
    }

    void VideoEncBitrateAdaption::setMinBitRate(int val)
    {
        min_bitrate = val;
    }

    int VideoEncBitrateAdaption::getLastBitrate()
    {
        return last_bitrate;
    }

    void VideoEncBitrateAdaption::setInitBitRate(int val)
    {
        prev_target_bitrate = val;
        last_bitrate = val;
    }

    int VideoEncBitrateAdaption::runBitrateAdaption()
    {
        int ret = 0;
        int64_t now = systemTime();
        /* handle the TMMBR request, and config the encoder
         * if the flag_tmmbr_drop is ture, drop comming tmmbr in the duration [5s]
         */
        if (target_bitrate > 0 &&
                (flag_tmmbr_ignore == false ||
                (flag_tmmbr_ignore == true &&
                (now - tm_tmmbr_ignore_start) > 5000000000LL))) {
            if (flag_tmmbr_ignore == true) {
                flag_tmmbr_ignore = false;
            }
            bitrate_raise_cnt = prev_target_bitrate < target_bitrate ?
                    bitrate_raise_cnt + 1 : 0;
            prev_target_bitrate = target_bitrate;
            // if the bitrate trend is upward, mark the flag_upgrade.
            if (bitrate_raise_cnt >= 3) {
                flag_upgrade = true;
            }

            if (target_bitrate > max_bitrate) {
                if (frame_rate_level != 1){
                    flag_tmmbr_ignore = true;
                }
                frame_rate_level = 1;
                target_bitrate = max_bitrate;
                flag_upgrade = false;
            } else if ((target_bitrate < min_bitrate) &&
                    (target_bitrate >= ((min_bitrate * 3) >> 2))) {
                if (frame_rate_level != 1){
                    flag_tmmbr_ignore = true;
                }
                frame_rate_level = 1;
                target_bitrate = min_bitrate;
                flag_upgrade = false;
            } else if(target_bitrate < ((min_bitrate * 3) >> 2)) { // if the target_bitrate < min_bitrate * 0.75
                switch (frame_rate_level) {
                    case 1:
                        if (target_bitrate > (min_bitrate >> 1)) {
                            frame_rate_level = 2;
                        } else if(target_bitrate < (min_bitrate >> 1)) {
                            frame_rate_level = 4;
                        }
                        //clear the trend cnt and the flag_upgrade
                        bitrate_raise_cnt = 0;
                        flag_upgrade = false;
                        target_bitrate = min_bitrate;
                        break;
                    case 2:
                    case 4:
                        if (target_bitrate > (min_bitrate >> 1)) {
                            if (flag_upgrade) {
                                frame_rate_level = 1;
                                bitrate_raise_cnt = 0;
                                flag_upgrade = false;
                                target_bitrate = min_bitrate;
                                tm_tmmbr_ignore_start = now;
                                flag_tmmbr_ignore = true;
                            } else {
                                frame_rate_level = 2;
                                target_bitrate = min_bitrate;
                            }
                        } else if(target_bitrate < (min_bitrate >> 1)) {
                            if (flag_upgrade) {
                                frame_rate_level = 2;
                                bitrate_raise_cnt = 0;
                                flag_upgrade = false;
                                target_bitrate = min_bitrate;
                                tm_tmmbr_ignore_start = now;
                                flag_tmmbr_ignore = true;
                            } else {
                                frame_rate_level = 4;
                                target_bitrate = min_bitrate;
                            }
                        }
                        break;
                    default:
                        break;
               }
            } else {
                frame_rate_level = 1;
                flag_upgrade = false;
            }
            ALOGI("Recv TMMBR %d, modify video-bitrate %d -> %d bps, upgrade %u, level %u, raise_cnt %d",
                    prev_target_bitrate, last_bitrate, target_bitrate, flag_upgrade, frame_rate_level, bitrate_raise_cnt);
            last_bitrate = target_bitrate;
            ret = target_bitrate;
            target_bitrate = -1;
        }
        return ret;
    }


    void* VideoCallEngineClient::CameraThreadWrapper(void* me)
    {
        return (void*)static_cast<VideoCallEngineClient*>(me)->CameraThreadFunc();
    }

    status_t VideoCallEngineClient::CameraThreadFunc()
    {
        ALOGI("camera thread start");
        int setname_ret = pthread_setname_np(pthread_self(), "vce_cam");
        if (0 != setname_ret){
            ALOGE("camera thread, set name failed, ret = %d", setname_ret);
        }
        char value[PROPERTY_VALUE_MAX];
        property_get("volte.incall.camera.enable", value, "false");
        if(strcmp(value, "true")){
            ALOGI("camera thread, camera prop enable");
            property_set("volte.incall.camera.enable", "true");
        }
        const char *rawClientName = "ASDFGH";
        int rawClientNameLen = 7;
        int ret;
        String16 clientName(rawClientName, rawClientNameLen);
        char fr_str[8] = {0};
        unsigned int frame_num = 0;
#if 1
        Size currentPreviewSize;
        int32_t framerate = 15;

        switch (mCameraSize) {
            case CAMERA_SIZE_VGA_30:
                currentPreviewSize.width = 480;
                currentPreviewSize.height = 640;
                framerate = 30;
                break;
            case CAMERA_SIZE_VGA_15:
                currentPreviewSize.width = 480;
                currentPreviewSize.height = 640;
                framerate = 15;
                break;
            case CAMERA_SIZE_720P:
                currentPreviewSize.width = 1280;
                currentPreviewSize.height = 720;
                framerate = 30;
                break;
            case CAMERA_SIZE_QVGA_15:
                currentPreviewSize.width = 240;
                currentPreviewSize.height = 320;
                framerate = 15;
                break;
            case CAMERA_SIZE_QVGA_30:
                currentPreviewSize.width = 240;
                currentPreviewSize.height = 320;
                framerate = 30;
                break;
            case CAMERA_SIZE_CIF:
                currentPreviewSize.width = 352;
                currentPreviewSize.height = 288;
                framerate = 30;
                break;
            case CAMERA_SIZE_QCIF:
                currentPreviewSize.width = 176;
                currentPreviewSize.height = 144;
                framerate = 30;
                break;
            default:
                ALOGI("camera thread, unsupported camera size or fps");
                break;
        }
        ALOGI("camera thread, output size %d X %d, fps = %d", currentPreviewSize.width,
                currentPreviewSize.height, framerate);

        sprintf(fr_str, "%d", framerate);
        ret = property_set("persist.volte.cmr.fps", fr_str);
        ALOGI("camera thread, property_set ret %d", ret);
        if ((mLocalSurface == NULL) || (mCamera== NULL) || (mCameraProxy== NULL)){
            ALOGI("camera thread end, mCamera %d, Proxy %d, mLocalSurface %d",
                    mCamera== NULL, mCameraProxy== NULL, mLocalSurface == NULL);
            mStopCamera = true;
            mCameraExitedCond.signal();
            return 0;
        }

        sp<CameraSource> source = CameraSource::CreateFromCamera(
            mCamera, mCameraProxy, -1, clientName, -1, currentPreviewSize, -1, NULL, true);
        status_t init_err = source->initCheck();
        ALOGI("camera thread, initCheck() = %d", init_err);
        if (init_err != OK){
            ALOGE("camera thread end, initCheck failed");
            mStopCamera = true;
            mCameraExitedCond.signal();
            return 0;
        }

        ALOGI("camera thread, source = %p camera = %p proxy = %p", source.get(), mCamera.get(), mCameraProxy.get());
        mCamera->sendCommand(CAMERA_CMD_ENABLE_SHUTTER_SOUND,0,0);
        //mCamera.clear();
        //mCameraProxy.clear();
#else
        Size currentPreviewSize;
        currentPreviewSize.height = 480;
        currentPreviewSize.width = 640;
        sp<ICamera> camera = NULL;
        sp<ICameraRecordingProxy> proxy = NULL;

        sp<CameraSource> source = CameraSource::CreateFromCamera(
            camera, proxy, 1, clientName, -1, currentPreviewSize, 30, mLocalSurface, true);

        //camera.clear();
        //proxy.clear();
#endif
        mCameraStartTimeUs = systemTime()/1000;
        MetaData* meta = new MetaData();
        meta->setInt64(kKeyTime, mCameraStartTimeUs);
        ALOGI("camera thread, source->start enter, startTimeUs=%llu", mCameraStartTimeUs);
        status_t err_start = source->start(meta);
        ALOGI("camera thread, source->start return, err = %d", err_start);
        if (err_start != (status_t)OK){
            ALOGE("camera thread end, source->start(), err = %d", err_start);
            mStopCamera = true;
        } else {
            status_t err_read = OK;
            while (err_read == OK && !mStopCamera)
            {
                MediaBuffer *mediaBuffer;
                err_read = source->read(&mediaBuffer);
                if (err_read!=OK)
                {
                    ALOGE("camera thread, source->read, err_read = %d", err_read);
                    break;
                }
                frame_num ++;
                if (getCameraOutputBufferSize() > 6){
                    /* there are 8 buffers only for camera out put,
                     * if size of mCameraOutputBuffers is nearly full,
                     * drop the current frmae
                     */
                    mediaBuffer->release();
                    dropFrame ++;
                    ALOGE("camera thread, drop one frame, dropFrame = %lld", dropFrame);
                } else {
                    if ((frame_num & (mEncBrAdapt->getFrameRateLevel() - 1)) == 0) {
                        mCameraOutputBufferLock.lock();
                        mCameraOutputBuffers.push_back(mediaBuffer);
                        mCameraOutputBufferLock.unlock();
                        pushFrame ++;
                    } else {
                        mediaBuffer->release();
                    }
                }
                mCameraOutputBufferCond.signal();
            }
            ALOGI("camera thread, stop source->read");
            while(!isCameraOutputBufferEmpty())
            {
                ALOGI("camera thread, clean mCameraOutputBuffers");
                MediaBuffer* mediaBuffer = *mCameraOutputBuffers.begin();
                mediaBuffer->release();
                cameraRelease ++;
                mCameraOutputBuffers.erase(mCameraOutputBuffers.begin());
            }
            ALOGI("camera thread, stop mCameraOutputBuffers clean");
            property_get("volte.incall.camera.enable", value, "false");
            if(strcmp(value, "false")){
                ALOGI("camera thread, camera prop disable");
                property_set("volte.incall.camera.enable", "false");
            }
            if (source != NULL){
                ALOGE("pushFrame=%lld,dropFrame=%lld,cameraRelease=%lld,encodeRelease=%lld",
                        pushFrame,dropFrame,cameraRelease,encodeRelease);
                status_t err_stop = source->stop();
                ALOGI("camera thread, source->stop() = %d", err_stop);
                mStopCamera = true;
            }
        }
        ALOGI("camera thread end");
        mCameraExitedCond.signal();
        return 0;
    }

    int VideoCallEngineClient::getCameraOutputBufferSize(){
        mCameraOutputBufferLock.lock();
        int size = mCameraOutputBuffers.size();
        mCameraOutputBufferLock.unlock();
        return size;
    }

    bool VideoCallEngineClient::isCameraOutputBufferEmpty(){
        mCameraOutputBufferLock.lock();
        bool isEmpty = mCameraOutputBuffers.empty();
        mCameraOutputBufferLock.unlock();
        return isEmpty;
    }


    // Start Audio Source(Downlink&Uplink) capturing
    void VideoCallEngineClient::startAudioCapture()
    {
        ALOGI("[Alan] startAudioCapture...");

        pthread_attr_t attr;
        pthread_attr_init(&attr);
        pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
        pthread_create(&mAudioCaptureThread, &attr, AudioCaptureThreadWrapper, this);
        pthread_attr_destroy(&attr);
    }

    void* VideoCallEngineClient::AudioCaptureThreadWrapper(void* me)
    {
        return (void*)static_cast<VideoCallEngineClient*>(me)->AudioCaptureThreadFunc();
    }

    status_t VideoCallEngineClient::AudioCaptureThreadFunc()
    {
        // Obtain Audio PCM data in this thread and encode to AMR format in RecordThread thread
        ALOGI("[Alan] AudioSourceThread...");
        status_t err = OK;
        sp<AudioSource> audioSource = new AudioSource(AUDIO_SOURCE_MIC,
                                                      String16(), /*changed for AndroidM */
                                                      8000 /* Sample Rate */,
                                                      1    /* Channel count */);

        err = audioSource->initCheck();
        err = audioSource->start();
        CHECK_EQ(err, status_t(OK));

        while (1) {

            MediaBuffer *mediaBuffer = NULL;
            err = audioSource->read(&mediaBuffer);
            CHECK_EQ(err, status_t(OK));

            ALOGI("[Alan] Audio mediaBuffer = %p, and mediaBuffer size = %zd", mediaBuffer, mediaBuffer->size());
            {
                Mutex::Autolock autoLock(mRecordedAudioBufferLock);
                mRecordedPCMBuffers.push_back(mediaBuffer);
                mRecordedAudioBufferCond.signal();
            }

        }

        // The recorded buffers will be released in RecordThread
        return audioSource->stop();

        return 0;
    }

    void* VideoCallEngineClient::EncodeThreadWrapper(void* me)
    {
        return (void*)static_cast<VideoCallEngineClient*>(me)->EncodeThreadFunc();
    }

    status_t VideoCallEngineClient::EncodeThreadFunc()
    {
        ALOGI("encode thread start");
        int setname_ret = pthread_setname_np(pthread_self(), "vce_enc");
        if (0 != setname_ret){
            ALOGE("set name for encode thread failed, ret = %d", setname_ret);
        }
#if DUMP_VIDEO_BS
        if (video_dump_enable && (!mFp_local_dump)) {
            ALOGI("video dump local start");
            mFp_local_dump = fopen("/data/misc/media/video_local.mp4","wb");
            if (!mFp_local_dump){
                ALOGE("create video_local.mp4 failed");
            }
        }
#endif
        ProcessState::self()->startThreadPool();
        status_t err = OK;
        CodecState state;

        bool first_send_frame = true;
        bool sps_pps_frame = true;
        bool need_send_sps_pps = true;
        int after_idr_frame_count = 30;
        int cur_bitrate = 0;
        int min_bitrate = 0;
        int max_bitrate = 0;
        int target_bitrate = 0;
        int first_frame = 1;
        unsigned int bitrate_act = 0;
        uint64_t enc_tol_size = 0;
        uint64_t enc_interval_size = 0;
        uint32_t enc_frame_tol_num = 0;
        uint32_t enc_frame_interval_num = 0;

        //char br_str[PROPERTY_VALUE_MAX] = {0};
        int64_t stop_tm;
        int64_t start_tm;

        VC_EncodedFrame firstFrame = {NULL, 0, 0, 0, 0};

        sp<AMessage> format = new AMessage;

        switch (mCameraSize) {
            case CAMERA_SIZE_VGA_30:
                cur_bitrate = 600000;
                max_bitrate = 980000;
                min_bitrate = 480000;
                format->setInt32("width", 480);
                format->setInt32("height", 640);
                format->setInt32("bitrate", cur_bitrate);
                format->setFloat("frame-rate", 30);
                ALOGI("encode CAMERA_SIZE_VGA_30");
                break;
            case CAMERA_SIZE_VGA_15:
                cur_bitrate = 400000;
                max_bitrate = 660000;
                min_bitrate = 400000;
                format->setInt32("width", 480);
                format->setInt32("height", 640);
                format->setInt32("bitrate", cur_bitrate);
                format->setFloat("frame-rate", 15);
                ALOGI("encode CAMERA_SIZE_VGA_15");
                break;
            case CAMERA_SIZE_720P:
                cur_bitrate = 3000000;
                max_bitrate = 4000000;
                min_bitrate = 1200000;
                format->setInt32("width", 1280);
                format->setInt32("height", 720);
                format->setInt32("bitrate", cur_bitrate);
                format->setFloat("frame-rate", 30);
                ALOGI("encode CAMERA_SIZE_720P");
                break;
            case CAMERA_SIZE_QVGA_15:
                cur_bitrate = 256000;
                max_bitrate = 372000;
                min_bitrate = 128000;
                format->setInt32("width", 240);
                format->setInt32("height", 320);
                format->setInt32("bitrate", cur_bitrate);
                format->setFloat("frame-rate", 15);
                ALOGI("encode CAMERA_SIZE_QVGA_15");
                break;
            case CAMERA_SIZE_QVGA_30:
                cur_bitrate = 384000;
                max_bitrate = 512000;
                min_bitrate = 200000;
                format->setInt32("width", 240);
                format->setInt32("height", 320);
                format->setInt32("bitrate", cur_bitrate);
                format->setFloat("frame-rate", 30);
                ALOGI("encode CAMERA_SIZE_QVGA_30");
                break;
            case CAMERA_SIZE_CIF:
                cur_bitrate = 400000;
                max_bitrate = 500000;
                min_bitrate = 200000;
                format->setInt32("width", 352);
                format->setInt32("height", 288);
                format->setInt32("bitrate", cur_bitrate);
                format->setFloat("frame-rate", 30);
                ALOGI("encode CAMERA_SIZE_CIF");
                break;
            case CAMERA_SIZE_QCIF:
                cur_bitrate = 100000;
                max_bitrate = 300000;
                min_bitrate = 100000;
                format->setInt32("width", 176);
                format->setInt32("height", 144);
                format->setInt32("bitrate", cur_bitrate);
                format->setFloat("frame-rate", 30);
                ALOGI("encode CAMERA_SIZE_QCIF");
                break;
            default:
                ALOGI("ENC: unsupported camera size or fps");
                break;
        }
        mEncBrAdapt->setFrameRateLevel(1);
        mEncBrAdapt->setInitBitRate(cur_bitrate);
        mEncBrAdapt->setMaxBitRate(max_bitrate);
        mEncBrAdapt->setMinBitRate(min_bitrate);

        format->setString("mime", "video/avc");
        format->setInt32("color-format", 0x7FD00001);
        format->setInt32("i-frame-interval", 1);
        format->setInt32("store-metadata-in-buffers", 1);
        format->setInt32("prepend-sps-pps-to-idr-frames", 1);

        sp<ALooper> looper = new ALooper;
        looper->start();

        state.mCodec = MediaCodec::CreateByType(looper, "video/avc", true);
        if(state.mCodec == NULL){
            ALOGE("encode thread end, state.mCodec == NULL");
            return CodecThreadEndProcessing(NULL, looper, &mStopEncode);
        }

        sp<MediaCodec> codec = state.mCodec;

        if ((status_t)OK !=
            codec->configure(format, NULL, NULL, MediaCodec::CONFIGURE_FLAG_ENCODE)){
            ALOGE("encode thread end, codec->configure failed");
            return CodecThreadEndProcessing(codec, looper, &mStopEncode);
        }
        if ((status_t)OK != codec->start()){
            ALOGE("encode thread end, codec->start failed");
            return CodecThreadEndProcessing(codec, looper, &mStopEncode);
        }
        if ((status_t)OK != codec->getInputBuffers(&state.mInBuffers)){
            ALOGE("encode thread end, codec->getInputBuffers failed");
            codec->stop();
            return CodecThreadEndProcessing(codec, looper, &mStopEncode);
        }
        if ((status_t)OK != codec->getOutputBuffers(&state.mOutBuffers)){
            ALOGE("encode thread end, codec->getOutputBuffers failed");
            codec->stop();
            return CodecThreadEndProcessing(codec, looper, &mStopEncode);
        }

        ALOGI("encode got %d input and %d output buffers", state.mInBuffers.size(), state.mOutBuffers.size());

        AString SceneMode = "Volte";
        sp<AMessage> format2 = new AMessage;
        format2->setString("scene-mode",SceneMode);
        ALOGI("encode thread, set scene-mode: Volte");
        status_t set_error = codec ->setParameters(format2);
        if ((status_t)OK != set_error){
                ALOGE("encode thread, codec->setParameters scene-mode failed, err = %d", set_error);
        }

        while (!mStopEncode)
        {
            if(mRequestIDRFrame && after_idr_frame_count >= 30)
            {
                ALOGI("request idr frame by remote");
                after_idr_frame_count = 0;
                codec->requestIDRFrame();
                mRequestIDRFrame = false;
            }
            size_t index;
            err = codec->dequeueInputBuffer(&index);
            if(err == OK)
            {
                MediaBuffer* mediaBuffer = NULL;

                while(isCameraOutputBufferEmpty() && !mStopEncode)
                {
                    //ALOGE("mCameraEncodeLock");
                    uint64 timeout = 500000000; //nano seconds, 500ms
                    int wait_err = mCameraOutputBufferCond.waitRelative(mCameraEncodeLock, timeout);
                    if (wait_err == -ETIMEDOUT){
                        ALOGI("mCameraEncodeLock unlock");
                    }
                    //ALOGE("mCameraEncodeunLock");
                }
                if(!mStopCamera && !mStopEncode)
                {
                    mCameraOutputBufferLock.lock();
                    mediaBuffer = *mCameraOutputBuffers.begin();
                    mCameraOutputBuffers.erase(mCameraOutputBuffers.begin());
                    mCameraOutputBufferLock.unlock();
                }
                else
                {
                    ALOGI("encode thread exit 1");
                    //mStopCamera = true;
                    break;
                }
                const sp<ABuffer> &buffer = state.mInBuffers.itemAt(index);

                memcpy(buffer->data(), mediaBuffer->data(), mediaBuffer->size());

                int64_t timeUs;
                mediaBuffer->meta_data()->findInt64(kKeyTime, &timeUs);
                mediaBuffer->release();
                encodeRelease ++;
                if (mStopCamera || mStopEncode){
                    ALOGI("encode thread exit 2");
                    break;
                }

                target_bitrate = mEncBrAdapt->runBitrateAdaption();
                if (target_bitrate) {
                    format->setInt32("video-bitrate", target_bitrate);
                    codec ->setParameters(format);
                }
                err = codec->queueInputBuffer(index, 0, buffer->size(), timeUs, 0);
                CHECK_EQ(err, (status_t)OK);
            }
            else
            {
               CHECK_EQ(err, -EAGAIN);
            }

            while(!mStopEncode)
            {
                BufferInfo info;
                if (mStopCamera || mStopEncode){
                    ALOGI("encode thread exit 3");
                    break;
                }
                status_t err = codec->dequeueOutputBuffer(&info.mIndex, &info.mOffset, &info.mSize, &info.mPresentationTimeUs, &info.mFlags);

                if (err == OK)
                {
                    const sp<ABuffer> &buffer = state.mOutBuffers.itemAt(info.mIndex);
                    uint32_t tsMs = (uint32_t)((mCameraStartTimeUs+info.mPresentationTimeUs) / 1000);
#if DUMP_VIDEO_BS
                    if(video_dump_enable && mFp_local_dump)
                    {
                        fwrite(buffer->data(), 1, buffer->size(), mFp_local_dump);
                    }
#endif

                    /**
                     * <b> According to RCS 5.1 Spec Section 2.7.1.2. </b>
                     * <br>Last Packet of Key (I-Frame) will have 8 bytes of RTP Header Extension.
                     * <br>The 1-byte payload will indicate Camera and frame orientation as transmitted on wire.
                     * <br>
                     * <br> 7 6 5 4 3 2 1 0
                     * <br>+-+-+-+-+-+-+-+-+
                     * <br>|U|U|U|U|C| ROT |
                     * <br>+-+-+-+-+-+-+-+-+
                     * <br>
                     * <br>MSB Bits (7-4) are reserved for Future use.
                     * <br>
                     * <br>The Camera bit (bit 3) value that goes in to the RTP Header Extension is:
                     * <br>0 - Front Camera
                     * <br>1 - Back Camera
                     * <br>
                     * <br>The MSB (bit 2) of ROT indicates if we need to flip horizontally or not.
                     * <br>The last 2 bits (bit 1 and 0) of ROT indicates the rotation 0, 270, 180 or 90.
                     * <br>
                     * <br>This payload byte goes into the RTP Header Extension.
                     */

                    if(sps_pps_frame) //first frame is always sps pps frame
                    {
                        ALOGI("get sps/pps");
                        uint8_t* data = (uint8_t*)malloc(buffer->size());
                        memcpy(data, buffer->data(), buffer->size());
                        firstFrame.data_ptr = data;
                        firstFrame.tsMs = tsMs;
                        firstFrame.length = buffer->size();
                        firstFrame.flags = info.mFlags;
                        firstFrame.rcsRtpExtnPayload = 0;

                        sps_pps_frame = false;
                    }
                    if(mStartUplink)
                    {
                        if (first_frame) {
                            start_tm = systemTime();
                            stop_tm = start_tm;
                            enc_tol_size = 0;
                            enc_interval_size = 0;
                            enc_frame_tol_num = 0;
                            enc_frame_interval_num = 0;
                            first_frame = 0;
                        } else{
                            int64_t now = systemTime();
                            if ((now - stop_tm) > 3000000000LL) {
                                unsigned int fr = (enc_frame_tol_num - enc_frame_interval_num) * 1000 / ((now - stop_tm)/1000000);
                                bitrate_act = (unsigned int)((enc_tol_size - enc_interval_size) * 8 * 1000 / ((now - stop_tm) / 1000000));
                                ALOGI("bitrate_act %u bps, frame_rate %u fps, start_tm %llu, stop_tm %llu, enc_tol_size %llu, frame_tol_num %u",
                                        bitrate_act, fr ,start_tm, stop_tm, enc_tol_size, enc_frame_tol_num);
                                //sprintf(br_str, "%u", bitrate_act);
                                //property_set("persist.volte.enc.bitrate_act", br_str);
                                stop_tm = now;
                                enc_interval_size = enc_tol_size;
                                enc_frame_interval_num = enc_frame_tol_num;
                            }
                        }

                        if(need_send_sps_pps)
                        {
                            firstFrame.tsMs = tsMs;
                            ALOGI("send length sps pps %d, tsMs %lld, mFlags %d",
                                firstFrame.length, firstFrame.tsMs, firstFrame.flags);
                            enc_tol_size += firstFrame.length;
                            enc_frame_tol_num ++;
                            VCI_sendEncodedFrame(&firstFrame);
                            free(firstFrame.data_ptr);
                            after_idr_frame_count ++;
                            need_send_sps_pps = false;
                        }
                        if(!first_send_frame)
                        {
                            VC_EncodedFrame frame = {NULL, 0, 0, 0, 0};
                            uint8_t* data = (uint8_t*)malloc(buffer->size());
                            memcpy(data, buffer->data(), buffer->size());
                            frame.data_ptr = data;
                            frame.tsMs = tsMs;
                            frame.length = buffer->size();
                            frame.flags = info.mFlags;
                            frame.rcsRtpExtnPayload = 0;

                            enc_tol_size += frame.length;
                            enc_frame_tol_num ++;
                            ALOGI("send length %d, tsMs %lld, mFlags %d", frame.length, frame.tsMs, frame.flags);
                            VCI_sendEncodedFrame(&frame);
                            free(frame.data_ptr);
                            after_idr_frame_count ++;
                        }
                        else if(info.mFlags == 1)
                        {
                            VC_EncodedFrame frame = {NULL, 0, 0, 0, 0};
                            uint8_t* data = (uint8_t*)malloc(buffer->size());
                            memcpy(data, buffer->data(), buffer->size());
                            frame.data_ptr = data;
                            frame.tsMs = tsMs;
                            frame.length = buffer->size();
                            frame.flags = info.mFlags;
                            frame.rcsRtpExtnPayload = 0;

                            enc_tol_size += frame.length;
                            enc_frame_tol_num ++;
                            ALOGI("send length first frame %d, tsMs %lld, mFlags %d", frame.length, frame.tsMs, frame.flags);
                            VCI_sendEncodedFrame(&frame);
                            free(frame.data_ptr);
                            after_idr_frame_count ++;
                            first_send_frame = false;
                        }
                        else
                        {
                            ALOGI("waiting I frame");
                        }

                        if(after_idr_frame_count > 10000)
                        {
                            after_idr_frame_count = 30;
                        }
                    }
                    else
                    {
                        //keep requesting idr frame for send idir
                        //frame out at once when get start encode event
                        //mRequestIDRFrame = true;
                        //after_idr_frame_count = 30;
                        //ALOGI("waiting send frame event tsMs %lld, mFlags %d", info.mPresentationTimeUs, info.mFlags);
                    }
                    err = codec->releaseOutputBuffer(info.mIndex);
                    CHECK_EQ(err, (status_t)OK);
                }
                else
                {
                    if (err == INFO_FORMAT_CHANGED)
                    {
                        ALOGI("INFO_FORMAT_CHANGED");
                        continue;
                    }
                    else if (err == INFO_OUTPUT_BUFFERS_CHANGED)
                    {
                        CHECK_EQ((status_t)OK, codec->getOutputBuffers(&state.mOutBuffers));
                        continue;
                    }
                    if (err == -EAGAIN)
                    {
                        err = OK;
                    }
                    break;
                }
            }
        }
        ALOGI("stopping encode, release codec");
        ALOGE("pushFrame=%lld,dropFrame=%lld,cameraRelease=%lld,encodeRelease=%lld",
                pushFrame,dropFrame,cameraRelease,encodeRelease);
        codec->stop();
        codec->release();
        codec.clear();
        ALOGI("stopping encode, release looper");
        looper->stop();
        looper.clear();

        if(need_send_sps_pps) //if not send sps pps, the frame data shuold be free
        {
            ALOGI("free sps pps frame which is not sent");
            free(firstFrame.data_ptr);
        }
        ALOGI("encode thread end");
        mEncodeExitedCond.signal();
        return 0;
       }

       status_t VideoCallEngineClient::CodecThreadEndProcessing(
               sp<MediaCodec> codec, sp<ALooper> looper, bool* mStopCodec)
       {
           ALOGI("codec thread end, CodecThreadEndProcessing E");
           if (codec != NULL){
               ALOGI("codec thread end, release codec");
               codec->release();
               codec.clear();
           }
           if (looper != NULL){
               ALOGI("codec thread end, release looper");
               looper->stop();
               looper.clear();
           }
           *mStopCodec = true;
           ALOGI("codec thread end, CodecThreadEndProcessing X");
           return 0;
       }

       unsigned int VideoCallEngineClient::SaveRemoteVideoParamSet(
               sp<ABuffer> paramSetWithPrefix, unsigned int* frame_length, unsigned char **frame){
           ALOGI("SaveRemoteVideoParamSet");
           unsigned int length = *frame_length;
           if (paramSetWithPrefix == NULL){
               return 0;
           }
           length = paramSetWithPrefix->size();
           if (*frame != NULL){
               ALOGI("SaveRemoteVideoParamSet, paramSet was saved , update");
               free(*frame);
               *frame = NULL;
           }
           *frame = (unsigned char*)malloc(length);
           if(*frame == NULL){
               ALOGI("SaveRemoteVideoParamSet, failed");
               return 0;
           }
           memcpy(*frame, paramSetWithPrefix->data(), length);
           *frame_length = length;
           ALOGI("SaveRemoteVideoParamSet, length = %d", length);
           return length;
       }

       unsigned int VideoCallEngineClient::SendLocalParamSetToDecoder(
               unsigned int ps_length, unsigned char *ps_data,
               vint* frame_length, unsigned char** frame_data){
           ALOGI("SendLocalParamSetToDecoder, ps_length = %d", ps_length);
           if (ps_data == NULL || (ps_length == 0)){
               ALOGI("SendLocalParamSetToDecoder, ps is null");
               return 0;
           }
           uint8* tmp_frame_data = (uint8*)malloc(*frame_length);
           if(tmp_frame_data == NULL){
               ALOGE("SendLocalParamSetToDecoder, failed");
               return 0;
           }
           memcpy(tmp_frame_data, *frame_data, *frame_length);
           free(*frame_data);
           *frame_data = (uint8*)malloc(*frame_length + ps_length);
           if(*frame_data == NULL){
               ALOGE("SendLocalParamSetToDecoder, failed 2");
               return 0;
           }
           memcpy(*frame_data, ps_data, ps_length);
           memcpy(*frame_data + ps_length, tmp_frame_data, *frame_length);
           *frame_length += ps_length;
           free(tmp_frame_data);
           return ps_length;
       }

       void* VideoCallEngineClient::DecodeThreadWrapper(void* me)
       {
           return (void*)static_cast<VideoCallEngineClient*>(me)->DecodeThreadFunc();
       }

       status_t VideoCallEngineClient::DecodeThreadFunc()
       {
        ALOGI("decode thread start");
        using_local_ps = true;
        uint8 rotate_value = 0;
        int setname_ret = pthread_setname_np(pthread_self(), "vce_dec");
        if (0 != setname_ret){
            ALOGE("set name for decode thread failed, ret = %d", setname_ret);
        }
#if DUMP_VIDEO_BS
        if (video_dump_enable && (!mFp_video)){
            ALOGI("video dump remote start");
            mFp_video = fopen("/data/misc/media/video_remote.mp4", "wb");
            if (!mFp_video){
                ALOGI("create video_remote.mp4 failed");
            }
        }
#endif
        status_t err;
        ProcessState::self()->startThreadPool();

        // remote render buffer transform
        uint32_t transform = 0;

        sp<ALooper> looper = new ALooper;
        looper->start();

        sp<AMessage> format = new AMessage;

        format->setInt32("width", 480);
        format->setInt32("height", 640);
        format->setString("mime", "video/avc");
        format->setInt32("bitrate", 1000000);
        format->setFloat("frame-rate", 30);
        format->setInt32("color-format", 0x7FD00001);
        format->setInt32("i-frame-interval", 10);
        format->setInt32("store-metadata-in-buffers", 1);

        int64_t idr_time = systemTime();

        CodecState state;
        state.mCodec = MediaCodec::CreateByType(looper, "video/avc", false);
        if(state.mCodec == NULL){
            ALOGE("decode thread end, state.mCodec == NULL");
            return CodecThreadEndProcessing(NULL, looper, &mStopDecode);
        }
        sp<MediaCodec> codec = state.mCodec;

        if(mRemoteSurface == NULL){
            ALOGE("decode thread end, mRemoteSurface == NULL");
            return CodecThreadEndProcessing(codec, looper, &mStopDecode);
        }
        sp<Surface> surface = new Surface(mRemoteSurface);

        err = state.mCodec->configure(format, surface, NULL, 0);
        if (err != (status_t)OK){
            ALOGE("decode thread end, state.mCodec->configure failed");
            return CodecThreadEndProcessing(codec, looper, &mStopDecode);
        }

        if ((status_t)OK != codec->start()){
            ALOGE("decode thread end, codec->start failed");
            return CodecThreadEndProcessing(codec, looper, &mStopDecode);
        }
        if ((status_t)OK != codec->getInputBuffers(&state.mInBuffers)){
            ALOGE("decode thread end, codec->getInputBuffers failed");
            codec->stop();
            return CodecThreadEndProcessing(codec, looper, &mStopDecode);
        }
        if ((status_t)OK != codec->getOutputBuffers(&state.mOutBuffers)){
            ALOGE("decode thread end, codec->getOutputBuffers failed");
            codec->stop();
            return CodecThreadEndProcessing(codec, looper, &mStopDecode);
        }

        ALOGI("decode got %d input and %d output buffers \n", state.mInBuffers.size(), state.mOutBuffers.size());

        while(!mStopDecode)
        {
            size_t index;
            err = codec->dequeueInputBuffer(&index, kTimeout);

            if (err == OK)
            {
                const sp<ABuffer> &buffer = state.mInBuffers.itemAt(index);

                VC_EncodedFrame frame = {NULL, 0, 0, 0, 0};
                int retry = 0;
                {
                    ALOGI("VCI_getEncodedFrame begin");
                    VCI_getEncodedFrame(&frame);
                    while(frame.length <= 0 && !mStopDecode)
                    {
                        usleep(20 * 1000);
                        VCI_getEncodedFrame(&frame);
                        retry ++;
                    }
                    if(mStopDecode)
                    {
                        ALOGE("decode thread exit 1");
                        if(frame.length > 0){
                            free(frame.data_ptr);
                        }
                        break;
                    }
                }
                ALOGI("get length %d, tsMs %lld, mFlags %d, rcsRtpExtnPayload %d, retry %d",
                    frame.length, frame.tsMs, frame.flags, frame.rcsRtpExtnPayload, retry);

#if ROTATE_ENABLE
                //frame.rcsRtpExtnPayload = 7;  //test only
                if (((frame.flags & MediaCodec::BUFFER_FLAG_SYNCFRAME) == 1)
                            && (rotate_value != frame.rcsRtpExtnPayload)) {
                    ALOGI("cvo, flags = %d, cvo = %d", frame.flags, frame.rcsRtpExtnPayload);
                    rotate_value = frame.rcsRtpExtnPayload;
                    uint8 rotate = frame.rcsRtpExtnPayload & 3;
                    transform = 0;
                    switch (rotate) {
                        case rotate_90:
                            transform = HAL_TRANSFORM_ROT_270;
                            break;
                        case rotate_180:
                            transform = HAL_TRANSFORM_ROT_180;
                            break;
                        case rotate_270:
                            transform = HAL_TRANSFORM_ROT_90;
                            break;
                        default:
                            break;
                    }
                    if(frame.rcsRtpExtnPayload & flip_h) {
                        transform = transform | HAL_TRANSFORM_FLIP_H;
                    }
                    ALOGI("cvo, transform = %d", transform);
                    sp<NativeWindowWrapper> wrapper = new NativeWindowWrapper(surface);
                    if (wrapper != NULL){
                        native_window_set_buffers_transform(wrapper->getNativeWindow().get(), transform);
                        ALOGI("cvo, mCodec->configure");
                        status_t err_reconfig = state.mCodec->configure(format, surface, NULL, 0);
                        if(err_reconfig != (status_t)OK){
                            ALOGI("cvo, err_reconfig = %d", err_reconfig);
                        }
                    }
                }
#endif

                //add sps/pps to the frist received frame(not sps/pps)
                if (using_local_ps){
                    using_local_ps = false;
                    if (2 != (frame.flags & MediaCodec::BUFFER_FLAG_CODECCONFIG)){
                        SendLocalParamSetToDecoder(
                                frame_pps_length, frame_pps, &frame.length, &frame.data_ptr);
                        SendLocalParamSetToDecoder(
                                frame_sps_length, frame_sps, &frame.length, &frame.data_ptr);
                        ALOGI("decode thread, send local sps(%d) pps(%d) to decoder, frame_len(%d)",
                            frame_sps_length, frame_pps_length, frame.length);
                    } else {
                        ALOGI("decode thread, no need to send local sps(%d) pps(%d) to decoder",
                                frame_sps_length, frame_pps_length);
                    }
                }

                buffer->meta()->setInt64("timeUs", frame.tsMs * 1000);
                buffer->setRange(0, frame.length);
                memcpy(buffer->data(), frame.data_ptr, frame.length);

                if (2 == (frame.flags & MediaCodec::BUFFER_FLAG_CODECCONFIG))
                {
                    sp<ABuffer> seqParamSet;
                    sp<ABuffer> picParamSet;
                    MakeAVCCodecSpecificData(buffer, seqParamSet, picParamSet);

                    if(seqParamSet != NULL)
                    {
                        sp<ABuffer> seqParamSetWithPrefix = new ABuffer(seqParamSet->size() + 4);
                        memcpy(seqParamSetWithPrefix->data(), "\x00\x00\x00\x01", 4);
                        memcpy(seqParamSetWithPrefix->data() + 4, seqParamSet->data(), seqParamSet->size());
#if 1
                        hexdump(seqParamSetWithPrefix->data(), seqParamSetWithPrefix->size());
#endif
                        ALOGI("[Alan] set csd-0");
                        mVideoFormat->setBuffer("csd-0", seqParamSetWithPrefix);
                        if(0 == (sps_pps_saved & 1)){
                            SaveRemoteVideoParamSet(
                                    seqParamSetWithPrefix, &frame_sps_length, &frame_sps);
                            sps_pps_saved |= 1;
                            ALOGI("decode thread, save sps(%d), sps_pps_saved(%x)",
                                    frame_sps_length, sps_pps_saved);
                        }
                    }
                    if(picParamSet != NULL)
                    {
                        sp<ABuffer> picParamSetWithPrefix = new ABuffer(picParamSet->size() + 4);
                        memcpy(picParamSetWithPrefix->data(), "\x00\x00\x00\x01", 4);
                        memcpy(picParamSetWithPrefix->data() + 4, picParamSet->data(), picParamSet->size());
#if 1
                        hexdump(picParamSetWithPrefix->data(), picParamSetWithPrefix->size());
#endif
                        ALOGI("[Alan] set csd-1");
                        mVideoFormat->setBuffer("csd-1", picParamSetWithPrefix);
                        if(0 == (sps_pps_saved & 2)){
                            SaveRemoteVideoParamSet(
                                    picParamSetWithPrefix, &frame_pps_length, &frame_pps);
                            sps_pps_saved |= 2;
                            ALOGI("decode thread, save pps(%d), sps_pps_saved(%x)",
                                    frame_pps_length, sps_pps_saved);
                        }
                    }
                }
                else if (1 == (frame.flags & MediaCodec::BUFFER_FLAG_SYNCFRAME))
                {
                    idr_time = systemTime();
                }
                if(idr_time > 0)
                {
                    int64_t now = systemTime();
                    unsigned int dur = (unsigned int)((now - idr_time) / 1000000L);
                    if(dur >= 2000 && !mStopDecode)
                    {
                        ALOGI("VCI_sendFIR as dur = %d", dur);
                        VCI_sendFIR();
                        idr_time = systemTime();
                    }
                }
#if DUMP_VIDEO_BS
                // Dump input video buffer(bs)
                if(video_dump_enable && mFp_video)
                {
                    fwrite(buffer->data(), 1, frame.length, mFp_video);
                }
#endif

#if RECORD_TEST
                // It's recoding so store encoded frames
                if (!mStopRecord) {
                    int64_t timeStamp = 0;

                    if (startSysTime == 0) {
                        startSysTime = systemTime();
                    } else {
                        timeStamp = systemTime() - startSysTime;
                    }

                    err = mMuxer->writeSampleData(buffer, mVideoTrack, timeStamp / 1000, frame.flags);
                    CHECK_EQ(err, status_t(OK));
                }

                if (counter++ >= MAX_INPUT_BUFFERS && !mStopRecord) {
                    ALOGI("[Alan] Stop MediaMux...");
                    mMuxer->stop();
                    mStopRecord = true;
                }
#endif
                if (mStopDecode){
                    ALOGE("decode thread exit 2");
                    if(frame.length > 0){
                        free(frame.data_ptr);
                    }
                    break;
                }
                err = codec->queueInputBuffer(index, buffer->offset(), buffer->size(), frame.tsMs * 1000, frame.flags);
                if (err != OK)
                {
                    ALOGE("Decode: Can't queueInputBuffer");
                }
                free(frame.data_ptr);
            }
            else
            {
               if(err != -EAGAIN)
               {
                   ALOGE("Decode: Can't queueInputBuffer, error != -EAGAIN");
                   mStopDecode = true;
               }
            }
            BufferInfo info;
            if (mStopDecode){
                ALOGE("decode thread exit 3");
                break;
            }
            err = codec->dequeueOutputBuffer(&info.mIndex, &info.mOffset, &info.mSize, &info.mPresentationTimeUs, &info.mFlags);
            if (err == OK)
            {
                if (mRemoteSurface!=NULL)
                {
                    codec->renderOutputBufferAndRelease(info.mIndex);
                }
                else
                {
                    codec->releaseOutputBuffer(info.mIndex);
                }
            }
            else if (err == INFO_OUTPUT_BUFFERS_CHANGED)
            {
                CHECK_EQ((status_t)OK, state.mCodec->getOutputBuffers(&state.mOutBuffers));
            }
            else if (err == INFO_FORMAT_CHANGED)
            {
                ALOGI("Decode: Can't dequeueOutputBuffer, error == INFO_FORMAT_CHANGED");
                sp<NativeWindowWrapper> wrapper = new NativeWindowWrapper(surface);
                if (wrapper != NULL){
                    ALOGI("decode thread, reset transform = %d", transform);
                    native_window_set_buffers_transform(wrapper->getNativeWindow().get(), transform);
                    status_t err_reconfig = state.mCodec->configure(format, surface, NULL, 0);
                }
            }
            else
            {
                if(err != -EAGAIN)
                {
                    ALOGE("Decode: Can't dequeueOutputBuffer, error != -EAGAIN");
                    mStopDecode = true;
                }
            }
        }
        ALOGI("stopping decode, release codec");
        codec->stop();
        codec->release();
        codec.clear();
        ALOGI("stopping decode, release looper");
        looper->stop();
        looper.clear();
        ALOGI("decode thread end");
        mDecodeExitedCond.signal();
        return 0;
       }

       void* VideoCallEngineClient::Switch2ThreadWrapper(void* me)
       {
           return (void*)static_cast<VideoCallEngineClient*>(me)->Switch2ThreadFunc();
       }

       status_t VideoCallEngineClient::Switch2ThreadFunc()
       {
           ALOGI("startSwitch");
           int setname_ret = pthread_setname_np(pthread_self(), "vce_back");
           if (0 != setname_ret){
               ALOGE("set name for Switch2ThreadFunc thread failed, ret = %d", setname_ret);
           }
           status_t err;
           ProcessState::self()->startThreadPool();
           while (!mStopSwitch)
           {
               VC_EncodedFrame frame = {NULL, 0, 0, 0, 0};
               int retry = 0;
               ALOGI("VCI_getEncodedFrame begin");
               VCI_getEncodedFrame(&frame);
               while(frame.length <= 0 && !mStopSwitch)
               {
                   usleep(20 * 1000);
                   VCI_getEncodedFrame(&frame);
                   ALOGI("VCI_getEncodedFrame");
                   retry ++;
               }
               free(frame.data_ptr);
           }
           mSwitchExitedCond.signal();
           ALOGI("Switch2ThreadFunc end");
           return 0;
           }
        void VideoCallEngineClient::startCamera()
        {
            if(mStopCamera &&  mCamera != NULL)
            {
                ALOGI("start camera");
                mStopCamera = false;
                pthread_attr_t attr;
                pthread_attr_init(&attr);
                pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
                pthread_create(&mCameraThread, &attr, CameraThreadWrapper, this);
                pthread_attr_destroy(&attr);
            }
            ALOGI("start camera thread end");
        }

        void VideoCallEngineClient::startEncode()
        {
            if(mStopCamera && mLocalSurface != NULL && mCamera != NULL)
            {
                startCamera();
                ALOGI("start encode");
                mStopEncode = false;
                mUplinkReady = true;
                pthread_attr_t attr;
                pthread_attr_init(&attr);
                pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
                pthread_create(&mEncodeThread, &attr, EncodeThreadWrapper, this);
                pthread_attr_destroy(&attr);

                if(mUpNetworkReady)
               {
                   mNotifyCallback->notifyCallback(VC_EVENT_START_ENC);
                   ALOGI("notifyCallback VC_EVENT_START_ENC");
               }
               else
               {
                   mNotifyCallback->notifyCallback(1000);
               }
           }
           else
           {
               ALOGI("mStopCamera = %d, mLocalSurface = %d, mCamera = %d",
                   mStopCamera, mLocalSurface != NULL, mCamera != NULL);
           }
    }
    void VideoCallEngineClient::setLocalSurface(sp<IGraphicBufferProducer> bufferProducer)
    {
        ALOGI("setSurface VCE_SERVICE_CAMERA");
        mLocalSurface = bufferProducer;
        if(bufferProducer != NULL)
        {
            startEncode();
        } else {
            stopUplink();
        }
    }

    void VideoCallEngineClient::setRemoteSurface(sp<IGraphicBufferProducer> bufferProducer)
    {
        ALOGI("setSurface VCE_SERVICE_DOWNLINK");
        mRemoteSurface = bufferProducer;
        mDownlinkReady=true;
        if (mDownNetworkReady && mRemoteSurface != NULL )
        {
            mNotifyCallback->notifyCallback(VC_EVENT_START_DEC);
            ALOGI("notifyCallback VC_EVENT_START_DEC");
        } else{
            ALOGI("startUplink, mDownNetworkReady = %d, (mRemoteSurface != NULL)? %d",
                    mDownNetworkReady, (mRemoteSurface != NULL));
        }
    }

    void VideoCallEngineClient::setCamera(const sp<ICamera>& camera, const sp<ICameraRecordingProxy>& proxy, VCE_CAMERA_SIZE camera_size)
    {
        ALOGI("setCamera, camera == NULL? %d", (camera == NULL));
        if (camera == NULL){
            stopUplink();
            mCamera = camera;
            mCameraProxy = proxy;
            mCameraSize = camera_size;
        } else {
            mCamera = camera;
            mCameraProxy = proxy;
            mCameraSize = camera_size;
            startEncode();
        }
    }

    void VideoCallEngineClient::init()
    {
        ALOGI("init");
        mRemoteSurface = NULL;
        mLocalSurface = NULL;
        mCamera = NULL;
        mCameraProxy = NULL;
        mStopSwitch = true;
        mStopDecode = true;
        mStopEncode = true;
        mStopCamera = true;
        mStopRecord = true;
        mStartUplink = false;
        mCameraSize = CAMERA_SIZE_VGA_30;
        mCallEnd = false;
        pushFrame = 0;
        dropFrame = 0;
        cameraRelease = 0;
        encodeRelease = 0;
        sps_pps_saved = 0;
    }

    VideoCallEngineClient::VideoCallEngineClient()
        : mRemoteSurface(NULL),
        mLocalSurface(NULL),
        mCamera(NULL),
        mCameraProxy(NULL),
        mPrevSampleTimeUs(0ll),
        mStopSwitch(true),
        mStopDecode(true),
        mStopEncode(true),
        mStopCamera(true),
        mStopRecord(true),
        mStopProcessEvent(true),
        mStartUplink(false),
        mCameraSize(CAMERA_SIZE_VGA_30),
        mRequestIDRFrame(false),
        mCallEnd(false),
        mUpNetworkReady(false),
        mUplinkReady(false),
        mDownNetworkReady(false),
        mDownlinkReady(false)
    {
        ALOGI("Constructor in VideoCallEngineClient...");
        // Audio PCM data dump file
#if DUMP_AUDIO_AMR
        mFp_audio = fopen("/data/misc/media/audio.bs","wb");
#endif

#if DUMP_VIDEO_BS
        mFp_video = NULL;
        mFp_local_dump = NULL;
#endif

#if DUMP_AUDIO_PCM
        mFp_pcm   = fopen("/data/misc/media/audio.pcm", "wb");
#endif

        mVideoFormat = new AMessage();
        mAudioFormat = new AMessage();
        mEncBrAdapt = new VideoEncBitrateAdaption();
    }

    VideoCallEngineClient::~VideoCallEngineClient()
    {
#if DUMP_AUDIO_AMR
        if (mFp_audio) {
            fclose(mFp_audio);
            mFp_audio = NULL;
        }
#endif

#if DUMP_AUDIO_PCM
        if (mFp_pcm) {
            fclose(mFp_pcm);
            mFp_pcm = NULL;
        }
#endif

#if DUMP_VIDEO_BS
        if (mFp_video)
        {
            fclose(mFp_video);
            mFp_video = NULL;
        }
        if (mFp_local_dump)
        {
            fclose(mFp_local_dump);
            mFp_local_dump = NULL;
        }
#endif
        if (mEncBrAdapt) {
            delete mEncBrAdapt;
            mEncBrAdapt = NULL;
        }
        //Release mAudioSource
        //delete mAudioSource;
    }

    void VideoCallEngineClient::startUplink()
    {
        ALOGI("startUplink");
        if(!mStartUplink)
        {
            mStartUplink = true;
        }
    }

    void VideoCallEngineClient::startDownlink()
    {
        mCallEnd=false;
        stopswitch2Background();
        if(mStopDecode)
        {
            ALOGI("startDownlink");
            mStopDecode = false;
            mDownNetworkReady = true;
            pthread_attr_t attr;
            pthread_attr_init(&attr);
            pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
            pthread_create(&mDecodeThread, &attr, DecodeThreadWrapper, this);
            pthread_attr_destroy(&attr);
        }
    }

    void VideoCallEngineClient::switch2Background()
    {
        if(mStopSwitch)
        {
            ALOGI("switch2Background");
            mStopSwitch=false;
            pthread_attr_t attr;
            pthread_attr_init(&attr);
            pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
            pthread_create(&mSwitch2Thread, &attr, Switch2ThreadWrapper, this);
            pthread_attr_destroy(&attr);
        }
    }

    void VideoCallEngineClient::stopUplink()
    {
       if(!mStopEncode)
        {
            ALOGI("stop encode thread");
            mStopEncode = true;
            ALOGI("mCameraOutputBufferCond.signal");
            mCameraOutputBufferCond.signal();
            uint64 time_out = 500000000; //nano seconds, 500ms
            int wait_err = mEncodeExitedCond.waitRelative(mEncodeExitedLock, time_out);
            if (wait_err == -ETIMEDOUT){
                ALOGE("stop encode thread time__out");
            }
            ALOGI("encode thread stopped");
        } else {
            ALOGI("encode thread has already been stopped");
        }
        stopCamera();
        mStartUplink = false;
        mUplinkReady = false;
    }

    void VideoCallEngineClient::stopCamera()
    {
        if(!mStopCamera)
        {
            ALOGI("stop camera thread");
            mStopCamera = true;
            int64_t timeout = 1000000000; //nano seconds, 1s
            status_t wait_err = mCameraExitedCond.waitRelative(mCameraExitedLock,timeout);
            ALOGI("stop camera thread wait_err = %d", wait_err);
            if (wait_err == -ETIMEDOUT){
                ALOGE("stop camera thread time__out");
            }
            ALOGI("stop camera thread end");
        } else {
            ALOGI("camera thread has already been stopped");
        }
    }

    void VideoCallEngineClient::stopDownlink()
    {
        ALOGI("stop downlink");
        if(!mStopDecode)
        {
            ALOGI("stop decode thread");
            mStopDecode = true;
            int64_t timeout = 500000000; //nano seconds, 500ms
            status_t wait_err = mDecodeExitedCond.waitRelative(mDecodeExitedLock, timeout);
            ALOGI("stop decode thread wait_err = %d", wait_err);
            if (wait_err == -ETIMEDOUT){
                ALOGE("stop decode thread time__out");
            }
            ALOGI("decode thread stopped");
        } else {
            ALOGI("decode thread has already been stopped");
        }
        mDownlinkReady = false;
        if (!mCallEnd)
        {
            switch2Background();
        }
    }

    void VideoCallEngineClient::stopswitch2Background()
    {
        ALOGI("mStopSwitch=%d",mStopSwitch);
        if(!mStopSwitch)
        {
            ALOGI("stop switch2Background");
            mStopSwitch=true;
            mSwitchExitedCond.wait(mSwitchExitedLock);
            if (!mCallEnd){
                ALOGI("swith to foreground, and ask for an I-frame with SPS/PPS");
                VCI_sendFIR();
            }
            ALOGI("switch2Background stopped");
        }
    }

    // Record remote video and Downlink&Uplink voice
    void VideoCallEngineClient::startRecord()
    {
        ALOGI("[Alan] startRecord...");
        status_t err = OK;
        startSysTime = 0;
        /*
         * O_CREAT: creat new file if file is not existing
         * O_TRUNC: clear the file content if file is existing
         * O_RDWR: read and write
         */
        int fd = open("/data/test.mp4",
                O_CREAT | O_LARGEFILE | O_TRUNC | O_RDWR, S_IRUSR | S_IWUSR);
        if (fd < 0) {
            ALOGI("ERROR: couldn't open file\n");
        }
        mMuxer = new MediaMuxer(fd, MediaMuxer::OUTPUT_FORMAT_MPEG_4);

        // Hard code csd-0 & csd-1
        // TODO: This is only for test purpose
        // We should extract sps/pps from codec-config-buffer
        sp<ABuffer> csd_0;
        sp<ABuffer> csd_1;

        mVideoFormat = new AMessage;
        mVideoFormat->setInt32("width", 720);
        mVideoFormat->setInt32("height", 480);
        mVideoFormat->setString("mime", "video/avc");
        mVideoFormat->setInt32("bitrate", 4000000);

        mVideoFormat->setFloat("frame-rate", 30);
        mVideoFormat->setInt32("color-format", 0x7FD00001);
        mVideoFormat->setInt32("i-frame-interval", 10);
        mVideoFormat->setInt32("store-metadata-in-buffers", 1);

        while ((mVideoFormat->findBuffer("csd-0", &csd_0) == false)
                || (mVideoFormat->findBuffer("csd-1", &csd_1) == false)) {
            ALOGI("[Alan] waitting for csd-0 & csd-1...");
            sleep(1);
        }

        ALOGI("[Alan] Got csd-0/csd-1...");
        hexdump(csd_0->data(), csd_0->size());
        hexdump(csd_1->data(), csd_1->size());

        csd_0 = new ABuffer(13);
        memcpy(csd_0->data(), "\x00\x00\x00\x01\x67\x42\xc0\x33\xe9\x01\x68\x7a\x20", 13);

        csd_1 = new ABuffer(8);
        memcpy(csd_1->data(), "\x00\x00\x00\x01\x68\xce\x3c\x80", 8);

        mVideoFormat->setBuffer("csd-0", csd_0);
        mVideoFormat->setBuffer("csd-1", csd_1);

        mAudioFormat = new AMessage;
        mAudioFormat->setString("mime", "audio/3gpp");
        mAudioFormat->setInt32("bitrate", 12200);
        mAudioFormat->setInt32("channel-count", 2);
        mAudioFormat->setInt32("sample-rate", 8000);
        mAudioFormat->setInt32("max-input-size", 8 * 1024);

        mVideoTrack = mMuxer->addTrack(mVideoFormat);
        mAudioTrack = mMuxer->addTrack(mAudioFormat);

        CHECK_GT(mVideoTrack, -1);
        CHECK_GT(mAudioTrack, -1);

        err = mMuxer->start();
        CHECK_EQ(err, status_t(OK));

        mStopRecord = false;

        pthread_attr_t attr;
        pthread_attr_init(&attr);
        pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
        pthread_create(&mRecordThread, &attr, AudioRecordThreadWrapper, this);
        pthread_attr_destroy(&attr);
    }

    void* VideoCallEngineClient::AudioRecordThreadWrapper(void* me)
    {
        return (void*)static_cast<VideoCallEngineClient*>(me)->AudioRecordThreadFunc();
    }

    // In this thread, we encode audio pcm data to AMR format then mux with video data
    status_t VideoCallEngineClient::AudioRecordThreadFunc()
    {
        ALOGI("[Alan] In RecordThreadFunc...");
        status_t err = OK;

        List<size_t> availPCMInputIndices;
        ProcessState::self()->startThreadPool();

        CodecState state;

        sp<ALooper> looper = new ALooper;
        looper->start();

        state.mCodec = MediaCodec::CreateByType(looper, "audio/3gpp", true);
        CHECK(state.mCodec != NULL);

        sp<MediaCodec> codec = state.mCodec;

        int sampleRate = 8000;

        sp<AMessage> audioFormat = new AMessage;
        audioFormat->setString("mime", "audio/3gpp");
        audioFormat->setInt32("bitrate", 12200);
        audioFormat->setInt32("channel-count", 2);
        audioFormat->setInt32("sample-rate", sampleRate);
        audioFormat->setInt32("max-input-size", 8 * 1024);

        err = codec->configure(audioFormat, NULL, NULL, MediaCodec::CONFIGURE_FLAG_ENCODE);
        CHECK_EQ(err, (status_t)OK);

        CHECK_EQ((status_t)OK, codec->start());
        CHECK_EQ((status_t)OK, codec->getInputBuffers(&state.mInBuffers));
        CHECK_EQ((status_t)OK, codec->getOutputBuffers(&state.mOutBuffers));

        ALOGI("[Alan] Encode got %d input and %d output buffers", state.mInBuffers.size(), state.mOutBuffers.size());

        while(!availPCMInputIndices.empty()) {
            availPCMInputIndices.erase(availPCMInputIndices.begin());
        }

        while (!mStopRecord) {

            while(!mStopRecord) {

                size_t bufferIndex;
                err = codec->dequeueInputBuffer(&bufferIndex);

                if (err != OK) break;

                availPCMInputIndices.push_back(bufferIndex);
            }

            while (!availPCMInputIndices.empty() && !mStopRecord) {

                size_t index = *availPCMInputIndices.begin();
                availPCMInputIndices.erase(availPCMInputIndices.begin());

                MediaBuffer* mediaBuffer = NULL;
                {
                    Mutex::Autolock autoLock(mRecordedAudioBufferLock);
                    while(mRecordedPCMBuffers.empty() && !mStopEncode)
                    {
                        mRecordedAudioBufferCond.wait(mRecordedAudioBufferLock);
                    }
                    mediaBuffer = *mRecordedPCMBuffers.begin();
                    mRecordedPCMBuffers.erase(mRecordedPCMBuffers.begin());
                }

                const sp<ABuffer> &buffer = state.mInBuffers.itemAt(index);
                ALOGI("[Alan] mediaBuffer->data() = %p, size = %zd", mediaBuffer->data(), mediaBuffer->size());
                memcpy(buffer->data(), mediaBuffer->data(), mediaBuffer->size());

#if DUMP_AUDIO_PCM
                // Dump Audio Data(PCM)
                fwrite(buffer->data(), 1, mediaBuffer->size(), mFp_pcm);
#endif
                int64_t size = mediaBuffer->size();
                uint32_t bufferFlags = 0;
                int64_t timestampUs = mPrevSampleTimeUs + ((1000000 * size / 2) + (sampleRate >> 1)) / sampleRate;
                err = codec->queueInputBuffer(index, 0, size, mPrevSampleTimeUs, bufferFlags);
                mPrevSampleTimeUs = timestampUs;

                if (err != OK) break;

            }

            while(!mStopRecord) {

                // dequeueOutputBuffer(Audio AMR) and mux it
                BufferInfo info;
                status_t err = codec->dequeueOutputBuffer(&info.mIndex, &info.mOffset, &info.mSize, &info.mPresentationTimeUs, &info.mFlags);

                if (err == OK) {
                    const sp<ABuffer> &buffer = state.mOutBuffers.itemAt(info.mIndex);

#if DUMP_AUDIO_AMR
                    ALOGI("[Alan] dump ecoded audio size = %zd", info.mSize);
                    // Dump Encoded Audio buffer(AMR)
                    fwrite(buffer->data(), 1, info.mSize, mFp_audio);
#endif

                    err = mMuxer->writeSampleData(buffer, mAudioTrack, info.mPresentationTimeUs, info.mFlags);

                    err = codec->releaseOutputBuffer(info.mIndex);
                    CHECK_EQ(err, (status_t)OK);

                } else {
                    if (err == INFO_FORMAT_CHANGED) {
                        ALOGI("[Alan] Got format INFO_FORMAT_CHANGED");
                        //codec->getOutputFormat(&mAudioFormat);
                        continue;
                    } else if (err == INFO_OUTPUT_BUFFERS_CHANGED) {
                        CHECK_EQ((status_t)OK, codec->getOutputBuffers(&state.mOutBuffers));
                        continue;
                    }
                    if (err == -EAGAIN) {
                        err = OK;
                    }
                    break;
                }
            }

        }

        codec->stop();
        codec->release();
        looper->stop();

        // Record stopped release mediabuffers
        while(!mRecordedPCMBuffers.empty() && mStopRecord) {
            ALOGI("[Alan] clean mRecordedBuffers...");
            MediaBuffer* mediaBuffer = *mRecordedPCMBuffers.begin();
            mediaBuffer->release();
            mRecordedPCMBuffers.erase(mRecordedPCMBuffers.begin());

            ALOGI("[Alan] muxer Stopped!");
            //mMuxer->stop();
        }

        return OK;
    }

    void VideoCallEngineClient::setNotifyCallback(const sp<IVideoCallEngineCallback>& callback)
    {
        mNotifyCallback = callback;
    }

    void VideoCallEngineClient::startProcessEvent()
    {
        property_get("persist.volte.video.dump", value_dump, default_value);
        video_dump_enable = !strcmp(value_dump, "true");
        ALOGI("startProcessEvent, video_dump_enable = %d", video_dump_enable);
        init();
        if(mStopProcessEvent)
        {
            ALOGI("startProcessEvent");
            mStopProcessEvent = false;
            pthread_attr_t attr;
            pthread_attr_init(&attr);
            pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
            pthread_create(&mProcessEventThread, &attr, ProcessEventThreadWrapper, this);
            pthread_attr_destroy(&attr);
            mInitCompleteCond.wait(mInitCompleteCondLock);
            ALOGI("startProcessEvent completed");
        }
    }

    void VideoCallEngineClient::stopProcessEvent()
    {
        if(!mStopProcessEvent)
        {
            mCallEnd=true;
            ALOGI("stopProcessEvent");
            stopUplink();
            stopDownlink();
            stopswitch2Background();
            mCamera = NULL;
            mCameraProxy = NULL;
            mLocalSurface = NULL;
            mRemoteSurface = NULL;
            mDownNetworkReady = false;
            mUpNetworkReady = false;
            video_dump_enable = false;
#if DUMP_VIDEO_BS
            if (mFp_video)
            {
                ALOGI("video dump remote end");
                fclose(mFp_video);
                mFp_video = NULL;
            }
            if (mFp_local_dump)
            {
                ALOGI("video dump local end");
                fclose(mFp_local_dump);
                mFp_local_dump = NULL;
            }
#endif
            //clear mark to save sps/pps, keep sps/pps
            sps_pps_saved = 0;
            ALOGI("ProcessEvent stopped");
        }
    }

    void* VideoCallEngineClient::ProcessEventThreadWrapper(void* me)
    {
        return (void*)static_cast<VideoCallEngineClient*>(me)->ProcessEventThreadFunc();
    }

    status_t VideoCallEngineClient::ProcessEventThreadFunc()
    {
        int setname_ret = pthread_setname_np(pthread_self(), "vce_evt");
        if (0 != setname_ret){
            ALOGE("set name for event process thread failed, ret = %d", setname_ret);
        }
        vint        ret;
        VC_Event    event;
        char        eventDesc[VCI_EVENT_DESC_STRING_SZ];
        vint        codecType;

        VIER_init();
        VCI_init();

        /* Currently, we only support H264 in Android.
         * "codecType" is not used.
         */
        while(!mStopProcessEvent)
        {
            ret = VCI_getEvent(&event, eventDesc, &codecType, -1);
            if(ret == 1 && !mStopProcessEvent)
            {
                switch(event)
                {
                    case VC_EVENT_NONE:
                        ALOGI("VC_EVENT_NONE");
                        break;

                    case VC_EVENT_INIT_COMPLETE  :
                        ALOGI("VC_EVENT_INIT_COMPLETE");
                        mInitCompleteCond.signal();
                        // Start Recording threads, for test purpose only
#if RECORD_TEST
                        startAudioCapture();
                        startRecord();
#endif
                        break;

                    case VC_EVENT_START_ENC   :
                        ALOGI("VC_EVENT_START_ENC");
                        mRequestIDRFrame = true;
                        mUpNetworkReady = true;
                        break;

                    case VC_EVENT_START_DEC   :
                        ALOGI("VC_EVENT_START_DEC");
                        mDownNetworkReady = true;
                        break;

                    case VC_EVENT_STOP_ENC  :
                        mUpNetworkReady = false;
                        ALOGI("VC_EVENT_STOP_ENC");
                        break;

                    case VC_EVENT_STOP_DEC   :
                        mDownNetworkReady = false;
                        ALOGI("VC_EVENT_STOP_DEC");
                        break;

                    case VC_EVENT_SHUTDOWN   :
                        ALOGI("VC_EVENT_SHUTDOWN");
                        //stopUplink();
                        //stopDownlink();
                        //mStopProcessEvent = true;
                        break;

                    case VC_EVENT_SEND_KEY_FRAME:
                        ALOGI("VC_EVENT_SEND_KEY_FRAME");
                        mRequestIDRFrame = true;
                        break;

                    case VC_EVENT_REMOTE_RECV_BW_KBPS:
                        mEncBrAdapt->setTargetBitRate(atoi(eventDesc) << 10);
                        ALOGI("VC_EVENT_REMOTE_RECV_BW_KBPS, mTargetBitRate=%d bps",
                                mEncBrAdapt->getTargetBitRate());
                        break;
                    case VC_EVENT_NO_RTP:
                        ALOGI("VC_EVENT_NO_RTP, for %s us", eventDesc);
                        break;
                    case VC_EVENT_PKT_LOSS_RATE:
                        ALOGI("VC_EVENT_PKT_LOSS_RATE, %s\%", eventDesc);
                        break;
                    default:
                        ALOGE("Receive a invalid EVENT");
                        break;
                }
                if(!mUplinkReady && VC_EVENT_START_ENC == event)
                {
                    ALOGI("should be waiting uplink ready");
                }
                else if (!mDownlinkReady && VC_EVENT_START_DEC == event)
                {
                    ALOGI("should be waiting downlink ready");
                }
                else
                {
                    mNotifyCallback->notifyCallback(event);
                }
            }
            else
            {
                ALOGI("invalide VC_EVENT");
            }
        }
        VCI_shutdown();
        VIER_shutdown();
        ALOGI("stopping process event");
        return OK;
    }

    int BpVideoCallEngineCallback::notifyCallback(int event)
    {
        ALOGI("BpVideoCallEngineCallback notifyCallback");
        Parcel data,reply;
        data.writeInt32(event);
        remote()->transact(VCE_ACTION_NOTIFY_CALLBACK, data, &reply);
        return reply.readInt32();
    }

    status_t BnVideoCallEngineCallback::onTransact(uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
    {
        switch (code)
        {
            case VCE_ACTION_NOTIFY_CALLBACK:
                {
                    ALOGI("BnVideoCallEngineCallback onTransact notifyCallback");
                    reply->writeInt32(notifyCallback((int)data.readInt32()));
                    return NO_ERROR;
                }
                break;
            default:
                return BBinder::onTransact(code, data, reply, flags);
        }
    }

    int VideoCallEngineCallback::notifyCallback(int event)
    {
        ALOGI("VideoCallEngineCallback notifyCallback");
        if(mListener != NULL)
        {
            mListener->notify(event, 0, 0, NULL);
        }
        else
        {
            ALOGI("listener has not been setup");
        }
        return 0;
    }

    status_t VideoCallEngineCallback::onTransact(uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
    {
        ALOGI("VideoCallEngineCallback onTransact notifyCallback");
        return BnVideoCallEngineCallback::onTransact(code, data, reply, flags);
    }

    void VideoCallEngineCallback::setListener(const sp<VideoCallEngineListener>& listner)
    {
        ALOGI("VideoCallEngineCallback setListener");
        mListener = listner;
    }
}
