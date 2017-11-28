package org.tensorflow.demo;

/**
 * Created by siddharth on 11/28/17.
 */

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.EyeTransform;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;
import com.squareup.seismic.ShakeDetector;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MonumentActivity extends CardboardActivity implements CardboardView.StereoRenderer, SurfaceTexture.OnFrameAvailableListener, ShakeDetector.Listener, SensorEventListener{

    private static final String TAG = "MainActivity";
    private static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;
    private Camera camera;

    private SensorManager sensorManager;
    private Sensor gsensor;
    private Sensor msensor;
    private float[] mGravity = new float[3];
    private float[] mGeomagnetic = new float[3];
    private float azimuth = 0f;
    private float currectAzimuth = 0;

    private final String vertexShaderCode =
            "attribute vec4 position;" +
                    "attribute vec2 inputTextureCoordinate;" +
                    "varying vec2 textureCoordinate;" +
                    "void main()" +
                    "{" +
                    "gl_Position = position;" +
                    "textureCoordinate = inputTextureCoordinate;" +
                    "}";

    private final String fragmentShaderCode =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;" +
                    "varying vec2 textureCoordinate;                            \n" +
                    "uniform samplerExternalOES s_texture;               \n" +
                    "void main(void) {" +
                    "  gl_FragColor = texture2D( s_texture, textureCoordinate );\n" +
                    //"  gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);\n" +
                    "}";

    private FloatBuffer vertexBuffer, textureVerticesBuffer, vertexBuffer2;
    private ShortBuffer drawListBuffer, buf2;
    private int mProgram;
    private int mPositionHandle, mPositionHandle2;
    private int mColorHandle;
    private int mTextureCoordHandle;

    int i=0;


    // number of coordinates per vertex in this array
    static final int COORDS_PER_VERTEX = 2;
    static float squareVertices[] = { // in counterclockwise order:
            -1.0f, -1.0f,   // 0.left - mid
            1.0f, -1.0f,   // 1. right - mid
            -1.0f, 1.0f,   // 2. left - top
            1.0f, 1.0f,   // 3. right - top
//
//    	 -1.0f, -1.0f, //4. left - bottom
//    	 1.0f , -1.0f, //5. right - bottom


//       -1.0f, -1.0f,  // 0. left-bottom
//        0.0f, -1.0f,   // 1. mid-bottom
//       -1.0f,  1.0f,   // 2. left-top
//        0.0f,  1.0f,   // 3. mid-top

            //1.0f, -1.0f,  // 4. right-bottom
            //1.0f, 1.0f,   // 5. right-top

    };


    //, 1, 4, 3, 4, 5, 3
//    private short drawOrder[] =  {0, 1, 2, 1, 3, 2 };//, 4, 5, 0, 5, 0, 1 }; // order to draw vertices
    private short drawOrder[] = {0, 2, 1, 1, 2, 3}; // order to draw vertices
    private short drawOrder2[] = {2, 0, 3, 3, 0, 1}; // order to draw vertices

    static float textureVertices[] = {
            0.0f, 1.0f,  // A. left-bottom
            1.0f, 1.0f,  // B. right-bottom
            0.0f, 0.0f,  // C. left-top
            1.0f, 0.0f   // D. right-top

//        1.0f,  1.0f,
//        1.0f,  0.0f,
//        0.0f,  1.0f,
//        0.0f,  0.0f
    };

    private final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    private ByteBuffer indexBuffer;    // Buffer for index-array

    private int texture;


    private CardboardOverlayView mOverlayView;


    private CardboardView cardboardView;
    private SurfaceTexture surface;
    private float[] mView;
    private float[] mCamera;

    //    adb shell input keyevent 8,9,10,11
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_1:
                mOverlayView.show3DToast("Detected Gesture: Bloom");
                return true;
            case KeyEvent.KEYCODE_2:
                mOverlayView.show3DToast("Detected Gesture: Click");
                return true;
            case KeyEvent.KEYCODE_3:
                mOverlayView.show3DToast("Detected Gesture: Zoom-In");
                return true;
            case KeyEvent.KEYCODE_4:
                mOverlayView.show3DToast("Detected Gesture: Zoom-Out");
                return true;
            default:
                return true;
        }
    }

    public void startCamera(int texture) {
        surface = new SurfaceTexture(texture);
        surface.setOnFrameAvailableListener(this);

        camera = Camera.open();

        try {
            camera.setPreviewTexture(surface);
            camera.startPreview();
        } catch (IOException ioe) {
            Log.w("MainActivity", "CAM LAUNCH FAILED");
        }
    }

    static private int createTexture() {
        int[] texture = new int[1];

        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture[0]);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        return texture[0];
    }


    /**
     * Converts a raw text file, saved as a resource, into an OpenGL ES shader
     *
     * @param type  The type of shader we will be creating.
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return
     */
    private int loadGLShader(int type, String code) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        if (shader == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shader;
    }

    /**
     * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
     *
     * @param func
     */
    private static void checkGLError(String func) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, func + ": glError " + error);
            throw new RuntimeException(func + ": glError " + error);
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.common_ui);
        cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);
        SensorManager sensorManager1 = (SensorManager) getSystemService(SENSOR_SERVICE);
        ShakeDetector sd = new ShakeDetector(this);
        sd.start(sensorManager1);

//        compass = new Compass(this);
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        gsensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        msensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorManager.registerListener(this, gsensor,
                SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, msensor,
                SensorManager.SENSOR_DELAY_GAME);


//        mModelCube = new float[16];
        mCamera = new float[16];
        mView = new float[16];
//
        mOverlayView = (CardboardOverlayView) findViewById(R.id.overlay);
        mOverlayView.show3DToast("Pull the magnet when you find an object.");
//        final Handler ha=new Handler();
//        ha.postDelayed(new Runnable() {
//
//            @Override
//            public void run() {
//                //call function
//                new CalcETA().execute("https://maps.googleapis.com/maps/api/distancematrix/json?units=imperial&origins=40.6655101,-73.89188969999998&destinations=40.6905615,-73.9976592&key=AIzaSyD95qQR9BUhWjpB2nmAsL3wu2QTJowwnFg");
//                ha.postDelayed(this, 10000);
//            }
//        }, 10000);

    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "start compass");
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "stop compass");
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged");
    }

    /**
     * Creates the buffers we use to store information about the 3D world. OpenGL doesn't use Java
     * arrays, but rather needs data in a format it can understand. Hence we use ByteBuffers.
     *
     * @param config The EGL configuration used when creating the surface.
     */
    @Override
    public void onSurfaceCreated(EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well

        ByteBuffer bb = ByteBuffer.allocateDirect(squareVertices.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(squareVertices);
        vertexBuffer.position(0);


        ByteBuffer dlb = ByteBuffer.allocateDirect(drawOrder.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);


        ByteBuffer bb2 = ByteBuffer.allocateDirect(textureVertices.length * 4);
        bb2.order(ByteOrder.nativeOrder());
        textureVerticesBuffer = bb2.asFloatBuffer();
        textureVerticesBuffer.put(textureVertices);
        textureVerticesBuffer.position(0);

        int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        mProgram = GLES20.glCreateProgram();             // create empty OpenGL ES Program
        GLES20.glAttachShader(mProgram, vertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(mProgram, fragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(mProgram);

        texture = createTexture();
        startCamera(texture);
    }


    /**
     * Prepares OpenGL ES before we draw a frame.
     *
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {


        float[] mtx = new float[16];
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        surface.updateTexImage();
        surface.getTransformMatrix(mtx);

    }

    @Override
    public void onFrameAvailable(SurfaceTexture arg0) {
        this.cardboardView.requestRender();

    }

    /**
     * Draws a frame for an eye. The transformation for that eye (from the camera) is passed in as
     * a parameter.
     *
     * @param transform The transformations to apply to render this eye.
     */
    @Override
    public void onDrawEye(EyeTransform transform) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        GLES20.glUseProgram(mProgram);

        GLES20.glActiveTexture(GL_TEXTURE_EXTERNAL_OES);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture);


        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "position");
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, vertexStride, vertexBuffer);


        mTextureCoordHandle = GLES20.glGetAttribLocation(mProgram, "inputTextureCoordinate");
        GLES20.glEnableVertexAttribArray(mTextureCoordHandle);
        GLES20.glVertexAttribPointer(mTextureCoordHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, vertexStride, textureVerticesBuffer);

        mColorHandle = GLES20.glGetAttribLocation(mProgram, "s_texture");


        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length,
                GLES20.GL_UNSIGNED_SHORT, drawListBuffer);


        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTextureCoordHandle);

        Matrix.multiplyMM(mView, 0, transform.getEyeView(), 0, mCamera, 0);


    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }

    @Override
    public void onCardboardTrigger() {
    }

    @Override
    public void hearShake() {
//        Toast.makeText(this, "Don't shake me, bro!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, CameraActivity.class);
        startActivity(intent);
        finish();    }

    public void adjustArrow()
    {
        Log.i(TAG, "will set rotation from " + currectAzimuth + " to "
                + azimuth);
        currectAzimuth = azimuth;
        if(azimuth>200.0 && azimuth<230.0){
            mOverlayView.show3DToast("Lotus temple - serves as the Mother Temple of the Indian subcontinent");
        }
        if(azimuth>90.0 && azimuth<130.0){
            mOverlayView.show3DToast("Tughlakabad Fort -  Home to the founder of Tughlaq dynasty");
        }

    }
    @Override
    public void onSensorChanged(SensorEvent event) {
        final float alpha = 0.97f;

        synchronized (this) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

                mGravity[0] = alpha * mGravity[0] + (1 - alpha)
                        * event.values[0];
                mGravity[1] = alpha * mGravity[1] + (1 - alpha)
                        * event.values[1];
                mGravity[2] = alpha * mGravity[2] + (1 - alpha)
                        * event.values[2];

                // mGravity = event.values;

                // Log.e(TAG, Float.toString(mGravity[0]));
            }

            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                // mGeomagnetic = event.values;

                mGeomagnetic[0] = alpha * mGeomagnetic[0] + (1 - alpha)
                        * event.values[0];
                mGeomagnetic[1] = alpha * mGeomagnetic[1] + (1 - alpha)
                        * event.values[1];
                mGeomagnetic[2] = alpha * mGeomagnetic[2] + (1 - alpha)
                        * event.values[2];
                // Log.e(TAG, Float.toString(event.values[0]));

            }

            float R[] = new float[9];
            float I[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, I, mGravity,
                    mGeomagnetic);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                // Log.d(TAG, "azimuth (rad): " + azimuth);
                azimuth = (float) Math.toDegrees(orientation[0]); // orientation
                azimuth = (azimuth + 360) % 360;
                // Log.d(TAG, "azimuth (deg): " + azimuth);
                adjustArrow();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
