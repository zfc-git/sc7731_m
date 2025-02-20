#ifndef ANDROID_VCEENGINE_H
#define ANDROID_VCEENGINE_H

#include <gui/ISurfaceComposer.h>
#include <gui/SurfaceComposerClient.h>
#include <gui/Surface.h>
#include <camera/Camera.h>
#include <camera/ICamera.h>
#include <camera/CameraParameters.h>

namespace android
{
    class VideoCallEngineListener;
    class VideoCallEngineProxy;
    class VideoCallEngine
    {
    public:
        void SetupVideoCall();
        void ReleaseVideoCall();

        void setRemoteSurface(const sp<IGraphicBufferProducer>& bufferProducer);
        void setLocalSurface(const sp<IGraphicBufferProducer>& bufferProducer);
        void setCamera(const sp<Camera>& camera, int camera_size);
        void stopCamera();
        void startCamera();
        void startUplink();
        void startDownlink();
        void stopUplink(bool uplink_started_for_ready = false);
        void stopDownlink();
        void setListener(const sp<VideoCallEngineListener>& listner);
        VideoCallEngine();
        ~VideoCallEngine();

    private:
        bool                        mIsRemoteSurfaceSet;
        bool                        mIsLocalSurfaceSet;
        bool                        mIsCameraSet;
        bool                        mIsSetup;
        bool                        mIsStartUplink;
        bool                        mIsStartDownlink;
        VideoCallEngineProxy*       mVideoCallEngineProxy;
    };
}

#endif
