#define LOG_NDEBUG 0
#define LOG_TAG "VideoCallEngineProxy"

#include <binder/IServiceManager.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/IPCThreadState.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/ALooper.h>

#include <camera/Camera.h>
#include <camera/ICamera.h>
#include <camera/CameraParameters.h>

#include "VideoCallEngineProxy.h"

namespace android
{
    static pthread_key_t sigbuskey;
    static sp<IBinder> s_video_call_engine_binder = NULL;
    static sp<DeathNotifier> sDeathNotifier;

    IMPLEMENT_META_INTERFACE(VideoCallEngineCallback, "android.videocallengine.callback");

    VideoCallEngineProxy::VideoCallEngineProxy()
    {
        ALOGI("VideoCallEngineProxy");
    }

    void VideoCallEngineProxy::setCamera(const sp<Camera>& camera, int camera_size)
    {
        ALOGI("setCamera");
        if (s_video_call_engine_binder == NULL){
            ALOGI("VideoCallEngineService is not ready");
            return;
        }

        Parcel data, reply;
        if(camera == NULL)
        {
            s_video_call_engine_binder->transact(VCE_ACTION_CLEAN_CAMERA, data, &reply);
        }
        else
        {
            //camera->unlock();
            sp<ICamera> icamera = camera->remote();
            sp<ICameraRecordingProxy> proxy = camera->getRecordingProxy();
            data.writeInt32(camera_size);
            data.writeStrongBinder(IInterface::asBinder(icamera));
            data.writeStrongBinder(IInterface::asBinder(proxy));
            s_video_call_engine_binder->transact(VCE_ACTION_SET_CAMERA, data, &reply);
        }
    }

    void VideoCallEngineProxy::startCamera()
    {
        ALOGI("startCamera");
        if (s_video_call_engine_binder == NULL){
            ALOGI("VideoCallEngineService is not ready");
            return;
        }
        Parcel data, reply;
        s_video_call_engine_binder->transact(VCE_ACTION_START_CAMERA, data, &reply);
    }

    void VideoCallEngineProxy::stopCamera()
    {
        ALOGI("stopCamera");
        if (s_video_call_engine_binder == NULL){
            ALOGI("VideoCallEngineService is not ready");
            return;
        }
        Parcel data, reply;
        s_video_call_engine_binder->transact(VCE_ACTION_STOP_CAMERA, data, &reply);
    }

    void VideoCallEngineProxy::setLocalSurface(const sp<IGraphicBufferProducer>& surface)
    {
        ALOGI("setLocalSurface");
        if (s_video_call_engine_binder == NULL){
            ALOGI("VideoCallEngineService is not ready");
            return;
        }
        Parcel data, reply;
        data.writeInt32(VCE_SERVICE_UPLINK);
        if(surface == NULL)
        {
            s_video_call_engine_binder->transact(VCE_ACTION_CLEAN_SURFACE, data, &reply);
        }
        else
        {
            data.writeStrongBinder(IInterface::asBinder(surface));
            s_video_call_engine_binder->transact(VCE_ACTION_SET_SURFACE, data, &reply);
        }
    }

    void VideoCallEngineProxy::setRemoteSurface(const sp<IGraphicBufferProducer>& surface)
    {
        ALOGI("setRemoteSurface");
        if (s_video_call_engine_binder == NULL){
            ALOGI("VideoCallEngineService is not ready");
            return;
        }
        Parcel data, reply;
        data.writeInt32(VCE_SERVICE_DOWNLINK);
        if(surface == NULL)
        {
            s_video_call_engine_binder->transact(VCE_ACTION_CLEAN_SURFACE, data, &reply);
        }
        else
        {
            data.writeStrongBinder(IInterface::asBinder(surface));
            s_video_call_engine_binder->transact(VCE_ACTION_SET_SURFACE, data, &reply);
        }
    }

    void VideoCallEngineProxy::CreateClient()
    {
        ALOGI("CreateClient");
        getVideoCallEngineService();
        Parcel data, reply;
        s_video_call_engine_binder->transact(VCE_ACTION_INIT, data, &reply);
    }

    void VideoCallEngineProxy::getVideoCallEngineService()
    {
        if(s_video_call_engine_binder == NULL)
        {
            ALOGI("getVideoCallEngineService()\n");
            sp<IServiceManager> sm = defaultServiceManager();
            do {
                s_video_call_engine_binder = sm->getService(String16("media.VideoCallEngineService"));
                if (s_video_call_engine_binder != NULL) {
                    ALOGI("client - getService: %p", sm.get());
                    break;
                } else {
                    ALOGI("VideoCallEngineService not published, retry...");
                }
                usleep(500000); // 0.5 s
            } while (true);
            if (sDeathNotifier == NULL) {
                sDeathNotifier = new DeathNotifier();
            }
            s_video_call_engine_binder->linkToDeath(sDeathNotifier);
        } else {
            ALOGI("no need to getVideoCallEngineService()\n");
        }
    }

    int VideoCallEngineProxy::startUplink()
    {
        ALOGI("startLink");
        if (s_video_call_engine_binder == NULL){
            ALOGI("VideoCallEngineService is not ready");
            return -1;
        }
        Parcel data, reply;
        data.writeInt32(VCE_SERVICE_UPLINK);
        s_video_call_engine_binder->transact(VCE_ACTION_STARTLINK, data, &reply);
        int r = reply.readInt32();
        return r;
    }

    int VideoCallEngineProxy::stopUplink()
    {
        ALOGI("stopLink");
        if (s_video_call_engine_binder == NULL){
            ALOGI("VideoCallEngineService is not ready");
            return -1;
        }
        Parcel data, reply;
        data.writeInt32(VCE_SERVICE_UPLINK);
        s_video_call_engine_binder->transact(VCE_ACTION_STOPLINK, data, &reply);
        int r = reply.readInt32();
        return r;
    }

    int VideoCallEngineProxy::startDownlink()
    {
        ALOGI("startDownlink");
        if (s_video_call_engine_binder == NULL){
            ALOGI("VideoCallEngineService is not ready");
            return -1;
        }
        Parcel data, reply;
        data.writeInt32(VCE_SERVICE_DOWNLINK);
        s_video_call_engine_binder->transact(VCE_ACTION_STARTLINK, data, &reply);
        int r = reply.readInt32();
        return r;
    }

    int VideoCallEngineProxy::stopDownlink()
    {
        ALOGI("stopDownlink");
        if (s_video_call_engine_binder == NULL){
            ALOGI("VideoCallEngineService is not ready");
            return -1;
        }
        Parcel data, reply;
        data.writeInt32(VCE_SERVICE_DOWNLINK);
        s_video_call_engine_binder->transact(VCE_ACTION_STOPLINK, data, &reply);
        int r = reply.readInt32();
        return r;
    }

    int VideoCallEngineProxy::Setup()
    {
        ALOGI("Setup");
        if (s_video_call_engine_binder == NULL){
            ALOGI("VideoCallEngineService is not ready");
            return -1;
        }
        Parcel data, reply;
        s_video_call_engine_binder->transact(VCE_ACTION_SETUP, data, &reply);
        int r = reply.readInt32();
        return r;
    }

    void VideoCallEngineProxy::setListener(const sp<VideoCallEngineListener>& listner)
    {
        ALOGI("setListener");
        if (s_video_call_engine_binder == NULL){
            ALOGI("VideoCallEngineService is not ready");
            return;
        }
        Parcel data, reply;
        mVideoCallEngineCallback = new VideoCallEngineCallback();
        mVideoCallEngineCallback->setListener(listner);

        data.writeStrongBinder(IInterface::asBinder(mVideoCallEngineCallback));
        s_video_call_engine_binder->transact(VCE_ACTION_SET_CALLBACK, data, &reply);
        int r = reply.readInt32();
    }

    status_t VideoCallEngineProxy::onTransact(uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
    {
        ALOGI("onTransact");
        return BBinder::onTransact(code, data, reply, flags);
    }

    int VideoCallEngineProxy::Release()
    {
        ALOGI("Release");
        if (s_video_call_engine_binder == NULL){
            ALOGI("VideoCallEngineService is not ready");
            return -1;
        }
        Parcel data, reply;
        s_video_call_engine_binder->transact(VCE_ACTION_RELEASE, data, &reply);
        int r = reply.readInt32();
        return r;
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

    void DeathNotifier::binderDied(const wp<IBinder>& who __unused){
        ALOGI("DeathNotifier::binderDied, media server died");
        s_video_call_engine_binder = NULL;
    }
}

