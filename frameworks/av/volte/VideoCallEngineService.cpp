#define LOG_NDEBUG 0
#define LOG_TAG "VideoCallEngineService"

#include <binder/IServiceManager.h>
#include <binder/IPCThreadState.h>
#include "VideoCallEngineService.h"
#include <binder/IServiceManager.h>
#include <binder/IPCThreadState.h>
#include "VideoCallEngineClient.h"
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/ALooper.h>


namespace android
{
    enum VCE_ACTION_TYPE{
        VCE_ACTION_INIT,
        VCE_ACTION_SET_SURFACE,
        VCE_ACTION_SET_CAMERA,
        VCE_ACTION_SET_CALLBACK,
        VCE_ACTION_SETUP,
        VCE_ACTION_STARTLINK,
        VCE_ACTION_STOPLINK,
        VCE_ACTION_CLEAN_SURFACE,
        VCE_ACTION_CLEAN_CAMERA,
        VCE_ACTION_RELEASE,
        VCE_ACTION_STOP_CAMERA,
        VCE_ACTION_START_CAMERA
    };

    static pthread_key_t sigbuskey;

    int VideoCallEngineService::instantiate()
    {
        ALOGI("Instantiate");
        int ret = defaultServiceManager()->addService(String16("media.VideoCallEngineService"), new VideoCallEngineService());
        ALOGI("ret = %d", ret);
        return ret;
    }

    VideoCallEngineService::VideoCallEngineService()
        : mVideoCallEngineClient(NULL)
    {
        ALOGI("VideoCallEngineService create");
        pthread_key_create(&sigbuskey,NULL);
    }

    VideoCallEngineService::~VideoCallEngineService()
    {
        pthread_key_delete(sigbuskey);
        printf("VideoCallEngineService destory\n");
        delete mVideoCallEngineClient;
        mVideoCallEngineClient = NULL;
    }

    status_t VideoCallEngineService::onTransact(uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
    {
        switch(code)
        {
        case VCE_ACTION_INIT:
            {
                ALOGI("onTransact VCE_ACTION_INIT");
                if(mVideoCallEngineClient == NULL)
                {
                    mVideoCallEngineClient = new VideoCallEngineClient();
                }
                reply->writeInt32(0);
                return NO_ERROR;
            }
            break;

       case VCE_ACTION_SET_SURFACE:
            {
                VCE_SERVICE_TYPE type = (VCE_SERVICE_TYPE)data.readInt32();

                sp<IGraphicBufferProducer> gbp(interface_cast<IGraphicBufferProducer>(data.readStrongBinder()));
                CHECK(gbp != NULL);

                if(type == VCE_SERVICE_DOWNLINK)
                {
                    ALOGI("setSurface downlink");
                    mVideoCallEngineClient->setRemoteSurface(gbp);
                }
                else if(type == VCE_SERVICE_UPLINK)
                {
                    ALOGI("setSurface uplink");
                    mVideoCallEngineClient->setLocalSurface(gbp);
                }
                reply->writeInt32(0);
                return NO_ERROR;
            }
            break;

       case VCE_ACTION_SET_CAMERA:
            {
                ALOGI("VCE_ACTION_SET_CAMERA");
                VCE_CAMERA_SIZE camera_size = (VCE_CAMERA_SIZE)data.readInt32();
                sp<ICamera> camera = interface_cast<ICamera>(data.readStrongBinder());
                sp<ICameraRecordingProxy> proxy = interface_cast<ICameraRecordingProxy>(data.readStrongBinder());

                mVideoCallEngineClient->setCamera(camera, proxy, camera_size);
                reply->writeInt32(0);
                return NO_ERROR;
            }
            break;

        case VCE_ACTION_START_CAMERA:
           {
               ALOGI("VCE_ACTION_START_CAMERA");
               mVideoCallEngineClient->startCamera();
               reply->writeInt32(0);
               return NO_ERROR;
           }
           break;

        case VCE_ACTION_STOP_CAMERA:
           {
               ALOGI("VCE_ACTION_STOP_CAMERA");
               mVideoCallEngineClient->stopCamera();
               reply->writeInt32(0);
               return NO_ERROR;
            }
            break;

        case VCE_ACTION_CLEAN_SURFACE:
            {
                VCE_SERVICE_TYPE type = (VCE_SERVICE_TYPE)data.readInt32();
                if(type == VCE_SERVICE_DOWNLINK)
                {
                    ALOGI("clean remote surface");
                    mVideoCallEngineClient->setRemoteSurface(NULL);
                }
                else if(type == VCE_SERVICE_UPLINK)
                {
                    ALOGI("clean local surface");
                    mVideoCallEngineClient->setLocalSurface(NULL);
                }
                reply->writeInt32(0);
                return NO_ERROR;
            }
            break;

        case VCE_ACTION_CLEAN_CAMERA:
            ALOGI("clean camera");
            mVideoCallEngineClient->setCamera(NULL, NULL, CAMERA_SIZE_VGA_30);
            reply->writeInt32(0);
            return NO_ERROR;
            break;

        case VCE_ACTION_RELEASE:
            {
                ALOGI("VCE_ACTION_RELEASE");
                release();
                reply->writeInt32(0);
                return NO_ERROR;
            }
            break;

        case VCE_ACTION_SETUP:
            mVideoCallEngineClient->startProcessEvent();
            reply->writeInt32(0);
            return NO_ERROR;
            break;

        case VCE_ACTION_STARTLINK:
            {
                VCE_SERVICE_TYPE type = (VCE_SERVICE_TYPE)data.readInt32();

                if(type == VCE_SERVICE_DOWNLINK)
                {
                    ALOGI("start downlink");
                    mVideoCallEngineClient->startDownlink();
                }
                else if(type == VCE_SERVICE_UPLINK)
                {
                    ALOGI("start uplink");
                    mVideoCallEngineClient->startUplink();
                }
            }
            reply->writeInt32(0);
            return NO_ERROR;
            break;

        case VCE_ACTION_SET_CALLBACK:
            {
                ALOGI("VCE_ACTION_SET_CALLBACK");
                sp<IVideoCallEngineCallback> notifyCallback = interface_cast<IVideoCallEngineCallback>(data.readStrongBinder());
                mVideoCallEngineClient->setNotifyCallback(notifyCallback);
            }
            reply->writeInt32(0);
            return NO_ERROR;
            break;

        case VCE_ACTION_STOPLINK:
            {
                VCE_SERVICE_TYPE type = (VCE_SERVICE_TYPE)data.readInt32();

                if(type == VCE_SERVICE_DOWNLINK)
                {
                    ALOGI("stop downlink");
                    mVideoCallEngineClient->stopDownlink();
                }
                else if(type == VCE_SERVICE_UPLINK)
                {
                    ALOGI("stop uplink");
                    mVideoCallEngineClient->stopUplink();
                }
            }
            reply->writeInt32(0);
            return NO_ERROR;
            break;

        default:
            return BBinder::onTransact(code, data, reply, flags);
        }
    }

    void VideoCallEngineService::release()
    {
        ALOGI("release");
        mVideoCallEngineClient->stopProcessEvent();
        //delete mVideoCallEngineClient;
        //mVideoCallEngineClient = NULL;
    }
}

