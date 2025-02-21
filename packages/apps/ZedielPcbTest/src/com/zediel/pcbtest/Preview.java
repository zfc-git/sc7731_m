package com.zediel.pcbtest;

import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

public class Preview extends SurfaceView implements SurfaceHolder.Callback {
	private SurfaceHolder mHolder;
	public Camera mCamera;
	private List<Size> mSupportedPreviewSizes;
	public Preview(Context context) {
		super(context);
		mHolder = getHolder();
		mHolder.addCallback(this);
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		Log.i("haisheng","<<<<< in the surfaceCreated >>>>");
		try {
			if (mCamera != null){
				mCamera.setPreviewDisplay(holder);
				openTorach(mCamera.getParameters());
			}
				
		} catch (IOException e) {
			mCamera.release();
			mCamera = null;
		}
	}
	 public void setCamera(Camera camera) {
	        mCamera = camera;
	        if (mCamera != null) {
	        	mSupportedPreviewSizes = mCamera.getParameters().getSupportedPreviewSizes();//
	            requestLayout();
	        }
	    }
	  public void switchCamera(Camera camera) {
		  setCamera(camera);
	       try {
	           camera.setPreviewDisplay(mHolder);
	       } catch (IOException exception) {
	    	    mCamera.release();
				mCamera = null;
	       }
	       Camera.Parameters parameters = camera.getParameters();
	       requestLayout();
//	       parameters.setPreviewSize(640, 480);
	       camera.setParameters(parameters);
	    }
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		if (mCamera != null) {
			Camera.Parameters parameters = mCamera.getParameters();
			// parameters.setPreviewSize(640, 480);//
			openTorach(parameters);
			Log.i("haisheng","<<<< in the surfaceChanged >>>>>");
			mCamera.setParameters(parameters);
			mCamera.startPreview();
		}
	}
	
	public void openTorach(Camera.Parameters parameters){
		CameraInfo info = new CameraInfo();
		Log.i("haisheng","<<<<< open the torch >>>>");
		if (info.facing == CameraInfo.CAMERA_FACING_BACK) {
			if (parameters.getFlashMode() == null) { //bukeyong
				Log.i("haisheng","&&&&&&& the flashMode is null");
				// continue
			} else {
				parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
				Log.i("haisheng", "<<<<<<< flash mode torch >>>");
			}
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (mCamera != null){
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}
}
