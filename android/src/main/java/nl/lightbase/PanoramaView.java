package nl.lightbase;

import android.media.ExifInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.AsyncTask;
import androidx.annotation.Nullable;
import android.util.Log;
import android.util.Pair;
import android.net.Uri;

import com.facebook.infer.annotation.Assertions;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.google.vr.sdk.widgets.pano.VrPanoramaEventListener;
import com.google.vr.sdk.widgets.pano.VrPanoramaView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;


public class PanoramaView extends VrPanoramaView implements LifecycleEventListener {
    private static final String LOG_TAG = "PanoramaView";
    private final static String SCHEME_FILE = "file";

    private VrPanoramaView.Options _options = new VrPanoramaView.Options();
    private ImageLoaderTask imageLoaderTask;

    private Integer imageWidth;
    private Integer imageHeight;
    private String imageUrl;
    private Bitmap image;
    private ThemedReactContext _context;


    public PanoramaView(ThemedReactContext context) {
        super(context.getCurrentActivity());

        _context = context;

        context.addLifecycleEventListener(this);

        this.setEventListener(new ActivityEventListener());
        this.setDisplayMode(DisplayMode.EMBEDDED);
        this.setStereoModeButtonEnabled(false);
        this.setTransitionViewEnabled(false);
        this.setInfoButtonEnabled(false);
        this.setFullscreenButtonEnabled(false);

    }



    public void setImageSource(String value) {
        //Log.i(LOG_TAG, "Image source: " + value);

        if(value == null){
            return;
        }

        if (imageUrl != null && imageUrl.equals(value)) {
            return;
        }

        imageUrl = value;

        try {
            if (imageLoaderTask != null) {
                imageLoaderTask.cancel(true);
            }

            imageLoaderTask = new ImageLoaderTask();
            imageLoaderTask.execute(imageUrl);

        } catch (Exception e) {
            emitImageLoadingFailed(e.toString());
        }
    }

    public void setEnableTouchTracking(boolean enableTouchTracking) {
        setTouchTrackingEnabled(enableTouchTracking);
    }

    class ImageLoaderTask extends AsyncTask<String, Void, Boolean> {

        protected Boolean doInBackground(String... fileInformation) {

            if(isCancelled()){
                return false;
            }

            InputStream istr = null;

            try {

                String value = fileInformation[0];
                Uri imageUri = Uri.parse(value);
                String scheme = imageUri.getScheme();
                String imagePath = imageUri.getPath();

                if(scheme == null || scheme.equalsIgnoreCase(SCHEME_FILE)){
                    istr = new FileInputStream(new File(imagePath));

                }
                else{
                    URL url = new URL(value);

                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.connect();

                    istr = connection.getInputStream();
                }

                Assertions.assertCondition(istr != null);
                image = decodeSampledBitmap(istr, imagePath);

            } catch (Exception e) {
                if(isCancelled()){
                    return false;
                }

                Log.e(LOG_TAG, "Could not load file: " + e);

                emitImageLoadingFailed("Failed to load source file.");
                return false;

            } finally {
                try {
                    if(istr != null){
                        istr.close();
                    }
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Could not close input stream: " + e);
                }
            }

            emitEvent("onImageDownloaded", null);
            loadImageFromBitmap(image, computeImageOptions(image));

            return true;
        }

        private Bitmap decodeSampledBitmap(InputStream inputStream, String inputPath) throws IOException {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            bitmap = rotateBitmapAccordingToExif(bitmap, inputPath);
            return bitmap;
        }

        private static Bitmap rotateBitmapAccordingToExif(Bitmap bitmap, String bitmapPath) throws IOException {
            int rotate = 0;
            ExifInterface exif = new ExifInterface(bitmapPath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(rotate);

            if (rotate == 0) {
                return bitmap;
            }

            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                bitmap.getHeight(), matrix, true);
        }

        // Detects if the image is spherical or cylindrical. This is the same function as iOS : 'private var panoramaTypeForCurrentImage: CTPanoramaType'
        private static VrPanoramaView.Options computeImageOptions(Bitmap bitmap) {
            VrPanoramaView.Options options = new VrPanoramaView.Options();
            if ((bitmap.getWidth() / bitmap.getHeight()) == 2) {
                // mono = spherical
                options.inputType = VrPanoramaView.Options.TYPE_MONO;
            } else {
                options.inputType = VrPanoramaView.Options.TYPE_STEREO_OVER_UNDER;
            }
            return options;
        }
    }

    private class ActivityEventListener extends VrPanoramaEventListener {
        @Override
        public void onLoadSuccess() {
            Log.i(LOG_TAG, "Image loaded.");

            emitEvent("onImageLoaded", null);
        }

        @Override
        public void onLoadError(String errorMessage) {
            Log.e(LOG_TAG, "Error loading panorama: " + errorMessage);
            emitImageLoadingFailed(errorMessage);
        }
    }

    private void emitImageLoadingFailed(String error) {
        WritableMap params = Arguments.createMap();
        params.putString("error", error);
        emitEvent("onImageLoadingFailed", params);
    }

    private void emitEvent(String name, @Nullable WritableMap event) {
        if (event == null) {
            event = Arguments.createMap();
        }

        _context.getJSModule(RCTEventEmitter.class).receiveEvent(
                getId(),
                name,
                event
        );
    }

    public void cleanUp(){
        if (imageLoaderTask != null) {
            imageLoaderTask.cancel(true);
        }

        this.setEventListener(null);
        _context.removeLifecycleEventListener(this);

        try{
            this.pauseRendering();
            this.shutdown();
        }
        catch(Exception e){

        }
    }

    @Override
    public void onHostResume() {
        //Log.i(LOG_TAG, "onHostResume");
    }

    @Override
    public void onHostPause() {
        //Log.i(LOG_TAG, "onHostPause");
    }

    @Override
    public void onHostDestroy() {
        this.cleanUp();
        //Log.i(LOG_TAG, "onHostDestroy");
    }
}
