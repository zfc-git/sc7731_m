#ifndef ANDROID_VCECLIENT_H
#define ANDROID_VCECLIENT_H

#include <gui/ISurfaceComposer.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/Surface.h>

#include <media/stagefright/foundation/AMessage.h>
#include <media/stagefright/foundation/AHandler.h>
//#include <media/stagefright/NativeWindowWrapper.h>
#include <media/stagefright/MediaMuxer.h>
#include <media/stagefright/AudioSource.h>
#include <media/stagefright/MediaCodec.h>

#include <camera/Camera.h>
#include <camera/ICamera.h>
#include <camera/CameraParameters.h>

#include <utils/Vector.h>
#include <utils/RefBase.h>

#include <binder/IInterface.h>
#include <binder/Parcel.h>
#include <binder/BinderService.h>

#include <system/audio.h>

#define DUMP_VIDEO_BS 1
#define DUMP_AUDIO_PCM 0
#define DUMP_AUDIO_AMR 0



namespace android
{
    // Surface derives from ANativeWindow which derives from multiple
    // base classes, in order to carry it in AMessages, we'll temporarily wrap it
    // into a NativeWindowWrapper.
    struct NativeWindowWrapper : RefBase {
        NativeWindowWrapper(
                const sp<Surface> &surfaceTextureClient) :
            mSurfaceTextureClient(surfaceTextureClient) { }

        sp<ANativeWindow> getNativeWindow() const {
            return mSurfaceTextureClient;
        }

        sp<Surface> getSurfaceTextureClient() const {
            return mSurfaceTextureClient;
        }

    private:
        const sp<Surface> mSurfaceTextureClient;

        DISALLOW_EVIL_CONSTRUCTORS(NativeWindowWrapper);
    };

    struct CodecState;
    struct BufferInfo;
    struct ABuffer;
    class MediaBuffer;

    class VideoCallEngineListener: virtual public RefBase
    {
    public:
        virtual void notify(int msg, int ext1, int ext2, const Parcel *obj) = 0;
    };

    class IVideoCallEngineCallback : public IInterface
    {
    public:
        DECLARE_META_INTERFACE(VideoCallEngineCallback);
        virtual int notifyCallback(int event) = 0;
    };

    class BnVideoCallEngineCallback : public BnInterface<IVideoCallEngineCallback>
    {
    public:
        virtual status_t onTransact( uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags = 0);
    };

    class BpVideoCallEngineCallback : public BpInterface<IVideoCallEngineCallback>
    {
    public:
        BpVideoCallEngineCallback(const sp<IBinder>& impl) : BpInterface<IVideoCallEngineCallback>(impl){}
        virtual int notifyCallback(int event);
    };

    IMPLEMENT_META_INTERFACE(VideoCallEngineCallback, "android.videocallengine.callback");

    class VideoCallEngineCallback: public BnVideoCallEngineCallback
    {
        friend class BinderService<VideoCallEngineCallback>;
    public:
        virtual int notifyCallback(int event);
        virtual status_t onTransact(uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags);
        void setListener(const sp<VideoCallEngineListener>& listner);

    private:
        sp<VideoCallEngineListener> mListener;
    };

    enum VCE_SERVICE_TYPE{
        VCE_SERVICE_DOWNLINK,
        VCE_SERVICE_UPLINK
    };

    enum VCE_CAMERA_SIZE{
        CAMERA_SIZE_720P = 0,
        CAMERA_SIZE_VGA_15= 1,
        CAMERA_SIZE_VGA_30= 2,
        CAMERA_SIZE_QVGA_15 = 3,
        CAMERA_SIZE_QVGA_30= 4,
        CAMERA_SIZE_CIF = 5,
        CAMERA_SIZE_QCIF = 6,
    };

    class VideoEncBitrateAdaption
    {
    public:
        VideoEncBitrateAdaption();
        ~VideoEncBitrateAdaption() {};

        void setTargetBitRate(int bitrate);
        int  getTargetBitRate();
        void setFrameRateLevel(int level);
        int getFrameRateLevel();
        void setMaxBitRate(int val);
        void setMinBitRate(int val);
        void setInitBitRate(int val);
        int getLastBitrate();
        void init();
        int runBitrateAdaption();
    private:
        bool flag_upgrade;
        bool flag_tmmbr_ignore;
        int bitrate_raise_cnt;
        int prev_target_bitrate;
        int max_bitrate;
        int min_bitrate;
        int target_bitrate;
        int last_bitrate;
        int frame_rate_level;
        int64_t tm_tmmbr_ignore_start;
    };

    struct VideoCallEngineClient
    {
        VideoCallEngineClient();
        virtual ~VideoCallEngineClient();
        void init();
        void startUplink();
        void stopUplink();
        void switch2Background();
        void stopswitch2Background();
        void startDownlink();
        void stopDownlink();
        void startProcessEvent();
        void stopProcessEvent();
        void startRecord();
        void stopRecord();
        void startAudioCapture();
        void stopCamera();
        void startCamera();

        void setLocalSurface(sp<IGraphicBufferProducer> bufferProducer);
        void setRemoteSurface(sp<IGraphicBufferProducer> bufferProducer);
        void setCamera(const sp<ICamera>& camera, const sp<ICameraRecordingProxy>& proxy, VCE_CAMERA_SIZE camera_size);
        void setNotifyCallback(const sp<IVideoCallEngineCallback>& callback);

    private:
        static void* CameraThreadWrapper(void *);
        status_t CameraThreadFunc();
        int getCameraOutputBufferSize();
        bool isCameraOutputBufferEmpty();
        status_t CodecThreadEndProcessing(sp<MediaCodec> codec, sp<ALooper> looper,
                bool* mStopCodec);
        static void* EncodeThreadWrapper(void *);
        status_t EncodeThreadFunc();
        unsigned int SaveRemoteVideoParamSet(
                sp<ABuffer> paramSetWithPrefix, unsigned int* frame_length,
                unsigned char **frame_data);
        unsigned int SendLocalParamSetToDecoder(
                unsigned int ps_length, unsigned char *ps_frame,
                int* frame_length, unsigned char **frame);
        static void* DecodeThreadWrapper(void *);
        status_t DecodeThreadFunc();
        static void* ProcessEventThreadWrapper(void *);
        status_t ProcessEventThreadFunc();
        static void* AudioRecordThreadWrapper(void *);
        status_t AudioRecordThreadFunc();
        static void* AudioCaptureThreadWrapper(void *);
        status_t AudioCaptureThreadFunc();
        static void* Switch2ThreadWrapper(void* );
        status_t Switch2ThreadFunc();
        void startEncode();

        sp<IGraphicBufferProducer>              mRemoteSurface;
        sp<IGraphicBufferProducer>              mLocalSurface;
        sp<ICamera>                             mCamera;
        sp<ICameraRecordingProxy>               mCameraProxy;
        VCE_CAMERA_SIZE                         mCameraSize;
        int64_t                                 mCameraStartTimeUs;

        pthread_t                               mCameraThread;
        pthread_t                               mEncodeThread;
        pthread_t                               mDecodeThread;
        pthread_t                               mSwitch2Thread;
        pthread_t                               mProcessEventThread;
        pthread_t                               mRecordThread;
        pthread_t                               mAudioCaptureThread;

        List<sp<ABuffer> >                      mEncodedBuffers;
        List<MediaBuffer*>                      mCameraOutputBuffers;
        /* List for for both Audio and Video buffers need to be recorded */
        List<MediaBuffer*>                      mRecordedPCMBuffers;

        Mutex                                   mCameraEncodeLock;
        Mutex                                   mEncodeDecodeLock;
        Mutex                                   mRecordedAudioBufferLock;
        Mutex                                   mCameraExitedLock;
        Mutex                                   mDecodeExitedLock;
        Mutex                                   mEncodeExitedLock;
        Mutex                                   mInitCompleteCondLock;
        Mutex                                   mSwitchExitedLock;
        Mutex                                   mCameraOutputBufferLock;

        Condition                               mCameraOutputBufferCond;
        Condition                               mEncodedBufferCond;
        Condition                               mRecordedAudioBufferCond;
        Condition                               mCameraExitedCond;
        Condition                               mDecodeExitedCond;
        Condition                               mEncodeExitedCond;
        Condition                               mInitCompleteCond;
        Condition                               mSwitchExitedCond;

        /* Audio Source for both Downlink and Uplink PCM voice */
        AudioSource                             *mAudioSource;

#if DUMP_AUDIO_AMR
        FILE                                    *mFp_audio;
#endif

#if DUMP_AUDIO_PCM
        FILE                                    *mFp_pcm;
#endif

#if DUMP_VIDEO_BS
        FILE                                    *mFp_video;
        FILE                                    *mFp_local_dump;
#endif

        sp<MediaMuxer>                          mMuxer;

        int64_t                                 mPrevSampleTimeUs;

        sp<AMessage>                            mVideoFormat;
        sp<AMessage>                            mAudioFormat;

        size_t                                  mVideoTrack;
        size_t                                  mAudioTrack;

        bool                                    mStopDecode;
        bool                                    mStopEncode;
        bool                                    mStopCamera;
        bool                                    mStopProcessEvent;
        bool                                    mStopRecord;
        bool                                    mStopSwitch;
        bool                                    mCallEnd;

        bool                                    mDecodeExited;
        bool                                    mEncodeExited;
        bool                                    mCameraExited;
        bool                                    mProcessEventExited;
        bool                                    mRequestIDRFrame;
        bool                                    mStartUplink;
        bool                                    mUplinkReady;
        bool                                    mUpNetworkReady;
        bool                                    mDownlinkReady;
        bool                                    mDownNetworkReady;

        sp<IVideoCallEngineCallback>            mNotifyCallback;
        VideoEncBitrateAdaption *               mEncBrAdapt;
    };
};


#endif

