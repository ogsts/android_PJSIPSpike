package com.jojonarte.pjsipspike;


import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import org.pjsip.pjsua2.*;


public class CallActivity extends Activity
        implements Handler.Callback, SurfaceHolder.Callback
{

    public static Handler handler_;
    private static VideoPreviewHandler previewHandler =
            new VideoPreviewHandler();

    private final Handler handler = new Handler(this);
    private static CallInfo lastCallInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        SurfaceView surfaceInVideo = (SurfaceView)
                findViewById(R.id.surfaceIncomingVideo);
        SurfaceView surfacePreview = (SurfaceView)
                findViewById(R.id.surfacePreviewCapture);
        Button buttonShowPreview = (Button)
                findViewById(R.id.buttonShowPreview);

        if (MainActivity.currentCall == null ||
                MainActivity.currentCall.vidWin == null)
        {
            surfaceInVideo.setVisibility(View.GONE);
            buttonShowPreview.setVisibility(View.GONE);
        }
        setupVideoPreview(surfacePreview, buttonShowPreview);
        surfaceInVideo.getHolder().addCallback(this);
        surfacePreview.getHolder().addCallback(previewHandler);

        handler_ = handler;
        if (MainActivity.currentCall != null) {
            try {
                lastCallInfo = MainActivity.currentCall.getInfo();
                updateCallState(lastCallInfo);
            } catch (Exception e) {
                System.out.println(e);
            }
        } else {
            updateCallState(lastCallInfo);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        WindowManager wm;
        Display display;
        int rotation;
        pjmedia_orient orient;

        wm = (WindowManager)this.getSystemService(Context.WINDOW_SERVICE);
        display = wm.getDefaultDisplay();
        rotation = display.getRotation();
        System.out.println("Device orientation changed: " + rotation);

        switch (rotation) {
            case Surface.ROTATION_0:   // Portrait
                orient = pjmedia_orient.PJMEDIA_ORIENT_ROTATE_270DEG;
                break;
            case Surface.ROTATION_90:  // Landscape, home button on the right
                orient = pjmedia_orient.PJMEDIA_ORIENT_NATURAL;
                break;
            case Surface.ROTATION_180:
                orient = pjmedia_orient.PJMEDIA_ORIENT_ROTATE_90DEG;
                break;
            case Surface.ROTATION_270: // Landscape, home button on the left
                orient = pjmedia_orient.PJMEDIA_ORIENT_ROTATE_180DEG;
                break;
            default:
                orient = pjmedia_orient.PJMEDIA_ORIENT_UNKNOWN;
        }

        if (MyApp.ep != null && MainActivity.account != null) {
            try {
                AccountConfig cfg = MainActivity.account.cfg;
                int cap_dev = cfg.getVideoConfig().getDefaultCaptureDevice();
                MyApp.ep.vidDevManager().setCaptureOrient(cap_dev, orient,
                        true);
            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        handler_ = null;
    }

    private void updateVideoWindow(boolean show)
    {
        if (MainActivity.currentCall != null &&
                MainActivity.currentCall.vidWin != null &&
                MainActivity.currentCall.vidPrev != null)
        {
            SurfaceView surfaceInVideo = (SurfaceView)
                    findViewById(R.id.surfaceIncomingVideo);

            VideoWindowHandle vidWH = new VideoWindowHandle();
            if (show) {
                vidWH.getHandle().setWindow(
                        surfaceInVideo.getHolder().getSurface());
            } else {
                vidWH.getHandle().setWindow(null);
            }
            try {
                MainActivity.currentCall.vidWin.setWindow(vidWH);
            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h)
    {
        updateVideoWindow(true);
    }

    public void surfaceCreated(SurfaceHolder holder)
    {
    }

    public void surfaceDestroyed(SurfaceHolder holder)
    {
        updateVideoWindow(false);
    }

    public void acceptCall(View view)
    {
        CallOpParam prm = new CallOpParam();
        prm.setStatusCode(pjsip_status_code.PJSIP_SC_OK);
        try {
            MainActivity.currentCall.answer(prm);
        } catch (Exception e) {
            System.out.println(e);
        }

        view.setVisibility(View.GONE);
    }

    public void hangupCall(View view)
    {
        handler_ = null;
        finish();

        if (MainActivity.currentCall != null) {
            CallOpParam prm = new CallOpParam();
            prm.setStatusCode(pjsip_status_code.PJSIP_SC_DECLINE);
            try {
                MainActivity.currentCall.hangup(prm);
            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }

    public void setupVideoPreview(SurfaceView surfacePreview,
                                  Button buttonShowPreview)
    {
        surfacePreview.setVisibility(previewHandler.videoPreviewActive?
                View.VISIBLE:View.GONE);

        buttonShowPreview.setText(previewHandler.videoPreviewActive?
                getString(R.string.hide_preview):
                getString(R.string.show_preview));
    }

    public void showPreview(View view)
    {
        SurfaceView surfacePreview = (SurfaceView)
                findViewById(R.id.surfacePreviewCapture);

        Button buttonShowPreview = (Button)
                findViewById(R.id.buttonShowPreview);


        previewHandler.videoPreviewActive = !previewHandler.videoPreviewActive;

        setupVideoPreview(surfacePreview, buttonShowPreview);

        previewHandler.updateVideoPreview(surfacePreview.getHolder());
    }

    private void setupVideoSurface()
    {
        SurfaceView surfaceInVideo = (SurfaceView)
                findViewById(R.id.surfaceIncomingVideo);
        SurfaceView surfacePreview = (SurfaceView)
                findViewById(R.id.surfacePreviewCapture);
        Button buttonShowPreview = (Button)
                findViewById(R.id.buttonShowPreview);
        surfaceInVideo.setVisibility(View.VISIBLE);
        buttonShowPreview.setVisibility(View.VISIBLE);
        surfacePreview.setVisibility(View.GONE);
    }

    @Override
    public boolean handleMessage(Message m)
    {
        if (m.what == MainActivity.MSG_TYPE.CALL_STATE) {

            lastCallInfo = (CallInfo) m.obj;
            updateCallState(lastCallInfo);

        } else if (m.what == MainActivity.MSG_TYPE.CALL_MEDIA_STATE) {

            if (MainActivity.currentCall.vidWin != null) {
		/* Set capture orientation according to current
		 * device orientation.
		 */
                onConfigurationChanged(getResources().getConfiguration());
		/* If there's incoming video, display it. */
                setupVideoSurface();
            }

        } else {

	    /* Message not handled */
            return false;

        }

        return true;
    }

    private void updateCallState(CallInfo ci) {
        TextView tvPeer  = (TextView) findViewById(R.id.textViewPeer);
        TextView tvState = (TextView) findViewById(R.id.textViewCallState);
        Button buttonHangup = (Button) findViewById(R.id.buttonHangup);
        Button buttonAccept = (Button) findViewById(R.id.buttonAccept);
        String call_state = "";

        if (ci.getRole() == pjsip_role_e.PJSIP_ROLE_UAC) {
            buttonAccept.setVisibility(View.GONE);
        }

        if (ci.getState().swigValue() <
                pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED.swigValue())
        {
            if (ci.getRole() == pjsip_role_e.PJSIP_ROLE_UAS) {
                call_state = "Incoming call..";
		/* Default button texts are already 'Accept' & 'Reject' */
            } else {
                buttonHangup.setText("Cancel");
                call_state = ci.getStateText();
            }
        }
        else if (ci.getState().swigValue() >=
                pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED.swigValue())
        {
            buttonAccept.setVisibility(View.GONE);
            call_state = ci.getStateText();
            if (ci.getState() == pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED) {
                buttonHangup.setText("Hangup");
            } else if (ci.getState() ==
                    pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED)
            {
                buttonHangup.setText("OK");
                call_state = "Call disconnected: " + ci.getLastReason();
            }
        }

        tvPeer.setText(ci.getRemoteUri());
        tvState.setText(call_state);
    }
}
