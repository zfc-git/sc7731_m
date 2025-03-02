/*
 * Copyright 2012, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef MEDIA_CODEC_H_

#define MEDIA_CODEC_H_

#include <gui/IGraphicBufferProducer.h>
#include <media/hardware/CryptoAPI.h>
#include <media/MediaResource.h>
#include <media/stagefright/foundation/AHandler.h>
#include <media/stagefright/FrameRenderTracker.h>
#include <utils/Vector.h>

namespace android {

struct ABuffer;
struct AMessage;
struct AReplyToken;
struct AString;
struct CodecBase;
struct IBatteryStats;
struct ICrypto;
class IMemory;
struct MemoryDealer;
class IResourceManagerClient;
class IResourceManagerService;
struct PersistentSurface;
struct SoftwareRenderer;
struct Surface;

struct MediaCodec : public AHandler {
    enum ConfigureFlags {
        CONFIGURE_FLAG_ENCODE   = 1,
        CONFIGURE_FLAG_THUMBNAIL   = 2,
    };

    enum BufferFlags {
        BUFFER_FLAG_SYNCFRAME   = 1,
        BUFFER_FLAG_CODECCONFIG = 2,
        BUFFER_FLAG_EOS         = 4,
    };

    enum {
        CB_INPUT_AVAILABLE = 1,
        CB_OUTPUT_AVAILABLE = 2,
        CB_ERROR = 3,
        CB_OUTPUT_FORMAT_CHANGED = 4,
        CB_RESOURCE_RECLAIMED = 5,
    };

    static const pid_t kNoPid = -1;

    static sp<MediaCodec> CreateByType(
            const sp<ALooper> &looper, const char *mime, bool encoder, status_t *err = NULL,
            pid_t pid = kNoPid);

    static sp<MediaCodec> CreateByComponentName(
            const sp<ALooper> &looper, const char *name, status_t *err = NULL,
            pid_t pid = kNoPid);

    static sp<PersistentSurface> CreatePersistentInputSurface();

    status_t configure(
            const sp<AMessage> &format,
            const sp<Surface> &nativeWindow,
            const sp<ICrypto> &crypto,
            uint32_t flags);

    status_t setCallback(const sp<AMessage> &callback);

    status_t setOnFrameRenderedNotification(const sp<AMessage> &notify);

    status_t createInputSurface(sp<IGraphicBufferProducer>* bufferProducer);

    status_t setInputSurface(const sp<PersistentSurface> &surface);

    status_t start();

    // Returns to a state in which the component remains allocated but
    // unconfigured.
    status_t stop();

    // Resets the codec to the INITIALIZED state.  Can be called after an error
    // has occured to make the codec usable.
    status_t reset();

    // Client MUST call release before releasing final reference to this
    // object.
    status_t release();

    status_t flush();

    status_t queueInputBuffer(
            size_t index,
            size_t offset,
            size_t size,
            int64_t presentationTimeUs,
            uint32_t flags,
            AString *errorDetailMsg = NULL);

    status_t queueSecureInputBuffer(
            size_t index,
            size_t offset,
            const CryptoPlugin::SubSample *subSamples,
            size_t numSubSamples,
            const uint8_t key[16],
            const uint8_t iv[16],
            CryptoPlugin::Mode mode,
            int64_t presentationTimeUs,
            uint32_t flags,
            AString *errorDetailMsg = NULL);

    status_t dequeueInputBuffer(size_t *index, int64_t timeoutUs = 0ll);

    status_t dequeueOutputBuffer(
            size_t *index,
            size_t *offset,
            size_t *size,
            int64_t *presentationTimeUs,
            uint32_t *flags,
            int64_t timeoutUs = 0ll);

    status_t renderOutputBufferAndRelease(size_t index, int64_t timestampNs);
    status_t renderOutputBufferAndRelease(size_t index);
    status_t releaseOutputBuffer(size_t index);

    status_t signalEndOfInputStream();

    status_t getOutputFormat(sp<AMessage> *format) const;
    status_t getInputFormat(sp<AMessage> *format) const;

    status_t getWidevineLegacyBuffers(Vector<sp<ABuffer> > *buffers) const;

    status_t getInputBuffers(Vector<sp<ABuffer> > *buffers) const;
    status_t getOutputBuffers(Vector<sp<ABuffer> > *buffers) const;

    status_t getOutputBuffer(size_t index, sp<ABuffer> *buffer);
    status_t getOutputFormat(size_t index, sp<AMessage> *format);
    status_t getInputBuffer(size_t index, sp<ABuffer> *buffer);

    status_t setSurface(const sp<Surface> &nativeWindow);

    status_t requestIDRFrame();

    // Notification will be posted once there "is something to do", i.e.
    // an input/output buffer has become available, a format change is
    // pending, an error is pending.
    void requestActivityNotification(const sp<AMessage> &notify);

    status_t getName(AString *componentName) const;

    status_t setParameters(const sp<AMessage> &params);

    // Create a MediaCodec notification message from a list of rendered or dropped render infos
    // by adding rendered frame information to a base notification message. Returns the number
    // of frames that were rendered.
    static size_t CreateFramesRenderedMessage(
            std::list<FrameRenderTracker::Info> done, sp<AMessage> &msg);

protected:
    virtual ~MediaCodec();
    virtual void onMessageReceived(const sp<AMessage> &msg);

private:
    // used by ResourceManagerClient
    status_t reclaim();
    friend struct ResourceManagerClient;

private:
    enum State {
        UNINITIALIZED,
        INITIALIZING,
        INITIALIZED,
        CONFIGURING,
        CONFIGURED,
        STARTING,
        STARTED,
        FLUSHING,
        FLUSHED,
        STOPPING,
        RELEASING,
    };

    enum {
        kPortIndexInput         = 0,
        kPortIndexOutput        = 1,
    };

    enum {
        kWhatInit                           = 'init',
        kWhatConfigure                      = 'conf',
        kWhatSetSurface                     = 'sSur',
        kWhatCreateInputSurface             = 'cisf',
        kWhatSetInputSurface                = 'sisf',
        kWhatStart                          = 'strt',
        kWhatStop                           = 'stop',
        kWhatRelease                        = 'rele',
        kWhatDequeueInputBuffer             = 'deqI',
        kWhatQueueInputBuffer               = 'queI',
        kWhatDequeueOutputBuffer            = 'deqO',
        kWhatReleaseOutputBuffer            = 'relO',
        kWhatSignalEndOfInputStream         = 'eois',
        kWhatGetBuffers                     = 'getB',
        kWhatFlush                          = 'flus',
        kWhatGetOutputFormat                = 'getO',
        kWhatGetInputFormat                 = 'getI',
        kWhatDequeueInputTimedOut           = 'dITO',
        kWhatDequeueOutputTimedOut          = 'dOTO',
        kWhatCodecNotify                    = 'codc',
        kWhatRequestIDRFrame                = 'ridr',
        kWhatRequestActivityNotification    = 'racN',
        kWhatGetName                        = 'getN',
        kWhatSetParameters                  = 'setP',
        kWhatSetCallback                    = 'setC',
        kWhatSetNotification                = 'setN',
    };

    enum {
        kFlagUsesSoftwareRenderer       = 1,
        kFlagOutputFormatChanged        = 2,
        kFlagOutputBuffersChanged       = 4,
        kFlagStickyError                = 8,
        kFlagDequeueInputPending        = 16,
        kFlagDequeueOutputPending       = 32,
        kFlagIsSecure                   = 64,
        kFlagSawMediaServerDie          = 128,
        kFlagIsEncoder                  = 256,
        kFlagGatherCodecSpecificData    = 512,
        kFlagIsAsync                    = 1024,
        kFlagIsComponentAllocated       = 2048,
        kFlagPushBlankBuffersOnShutdown = 4096,
    };

    struct BufferInfo {
        uint32_t mBufferID;
        sp<ABuffer> mData;
        sp<ABuffer> mEncryptedData;
        sp<IMemory> mSharedEncryptedBuffer;
        sp<AMessage> mNotify;
        sp<AMessage> mFormat;
        bool mOwnedByClient;
    };

    struct ResourceManagerServiceProxy : public IBinder::DeathRecipient {
        ResourceManagerServiceProxy(pid_t pid);
        ~ResourceManagerServiceProxy();

        void init();

        // implements DeathRecipient
        virtual void binderDied(const wp<IBinder>& /*who*/);

        void addResource(
                int64_t clientId,
                const sp<IResourceManagerClient> client,
                const Vector<MediaResource> &resources);

        void removeResource(int64_t clientId);

        bool reclaimResource(const Vector<MediaResource> &resources);

    private:
        Mutex mLock;
        sp<IResourceManagerService> mService;
        pid_t mPid;
    };

    State mState;
    bool mReleasedByResourceManager;
    sp<ALooper> mLooper;
    sp<ALooper> mCodecLooper;
    sp<CodecBase> mCodec;
    AString mComponentName;
    sp<AReplyToken> mReplyID;
    uint32_t mFlags;
    status_t mStickyError;
    sp<Surface> mSurface;
    SoftwareRenderer *mSoftRenderer;

    sp<AMessage> mOutputFormat;
    sp<AMessage> mInputFormat;
    sp<AMessage> mCallback;
    sp<AMessage> mOnFrameRenderedNotification;
    sp<MemoryDealer> mDealer;

    sp<IResourceManagerClient> mResourceManagerClient;
    sp<ResourceManagerServiceProxy> mResourceManagerService;

    bool mBatteryStatNotified;
    bool mIsVideo;
    int32_t mVideoWidth;
    int32_t mVideoHeight;
    int32_t mRotationDegrees;

    // initial create parameters
    AString mInitName;
    bool mInitNameIsType;
    bool mInitIsEncoder;

    // configure parameter
    sp<AMessage> mConfigureMsg;

    // Used only to synchronize asynchronous getBufferAndFormat
    // across all the other (synchronous) buffer state change
    // operations, such as de/queueIn/OutputBuffer, start and
    // stop/flush/reset/release.
    Mutex mBufferLock;

    List<size_t> mAvailPortBuffers[2];
    Vector<BufferInfo> mPortBuffers[2];

    int32_t mDequeueInputTimeoutGeneration;
    sp<AReplyToken> mDequeueInputReplyID;

    int32_t mDequeueOutputTimeoutGeneration;
    sp<AReplyToken> mDequeueOutputReplyID;

    sp<ICrypto> mCrypto;

    List<sp<ABuffer> > mCSD;

    sp<AMessage> mActivityNotify;

    bool mHaveInputSurface;
    bool mHavePendingInputBuffers;

    MediaCodec(const sp<ALooper> &looper, pid_t pid);

    static status_t PostAndAwaitResponse(
            const sp<AMessage> &msg, sp<AMessage> *response);

    void PostReplyWithError(const sp<AReplyToken> &replyID, int32_t err);

    status_t init(const AString &name, bool nameIsType, bool encoder);

    void setState(State newState);
    void returnBuffersToCodec();
    void returnBuffersToCodecOnPort(int32_t portIndex);
    size_t updateBuffers(int32_t portIndex, const sp<AMessage> &msg);
    status_t onQueueInputBuffer(const sp<AMessage> &msg);
    status_t onReleaseOutputBuffer(const sp<AMessage> &msg);
    ssize_t dequeuePortBuffer(int32_t portIndex);

    status_t getBufferAndFormat(
            size_t portIndex, size_t index,
            sp<ABuffer> *buffer, sp<AMessage> *format);

    bool handleDequeueInputBuffer(const sp<AReplyToken> &replyID, bool newRequest = false);
    bool handleDequeueOutputBuffer(const sp<AReplyToken> &replyID, bool newRequest = false);
    void cancelPendingDequeueOperations();

    void extractCSD(const sp<AMessage> &format);
    status_t queueCSDInputBuffer(size_t bufferIndex);

    status_t handleSetSurface(const sp<Surface> &surface);
    status_t connectToSurface(const sp<Surface> &surface);
    status_t disconnectFromSurface();

    void postActivityNotificationIfPossible();

    void onInputBufferAvailable();
    void onOutputBufferAvailable();
    void onError(status_t err, int32_t actionCode, const char *detail = NULL);
    void onOutputFormatChanged();

    status_t onSetParameters(const sp<AMessage> &params);

    status_t amendOutputFormatWithCodecSpecificData(const sp<ABuffer> &buffer);
    void updateBatteryStat();
    bool isExecuting() const;

    uint64_t getGraphicBufferSize();
    void addResource(const String8 &type, const String8 &subtype, uint64_t value);

    /* called to get the last codec error when the sticky flag is set.
     * if no such codec error is found, returns UNKNOWN_ERROR.
     */
    inline status_t getStickyError() const {
        return mStickyError != 0 ? mStickyError : UNKNOWN_ERROR;
    }

    inline void setStickyError(status_t err) {
        mFlags |= kFlagStickyError;
        mStickyError = err;
    }

    DISALLOW_EVIL_CONSTRUCTORS(MediaCodec);
};

}  // namespace android

#endif  // MEDIA_CODEC_H_
