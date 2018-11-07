package cn.reactnative.modules.qq;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;
import com.facebook.react.views.imagehelper.ResourceDrawableIdHelper;
import com.tencent.connect.common.Constants;
import com.tencent.connect.share.QQShare;
import com.tencent.tauth.IUiListener;
import com.tencent.tauth.Tencent;
import com.tencent.tauth.UiError;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by tdzl2_000 on 2015-10-10.
 *
 * Modified by Renguang Dong on 2016-05-25.
 */
public class QQModule extends ReactContextBaseJavaModule implements IUiListener, ActivityEventListener {
    private static final String TAG ="QQShare__" ;
    private String appId;
    private Tencent api;
    private final static String INVOKE_FAILED = "QQ API invoke returns false.";
    private boolean isLogin;

    private static final String RCTQQShareTypeNews = "news";
    private static final String RCTQQShareTypeImage = "image";
    private static final String RCTQQShareTypeImageBase64 = "imageBase64";
    private static final String RCTQQShareTypeText = "text";
    private static final String RCTQQShareTypeVideo = "video";
    private static final String RCTQQShareTypeAudio = "audio";

    private static final String RCTQQShareType = "type";
    private static final String RCTQQShareText = "text";
    private static final String RCTQQShareTitle = "title";
    private static final String RCTQQShareDescription = "description";
    private static final String RCTQQShareWebpageUrl = "webpageUrl";
    private static final String RCTQQShareImageUrl = "imageUrl";

    private static final int SHARE_RESULT_CODE_SUCCESSFUL = 0;
    private static final int SHARE_RESULT_CODE_FAILED = 1;
    private static final int SHARE_RESULT_CODE_CANCEL = 2;

    public QQModule(ReactApplicationContext context) {
        super(context);
        ApplicationInfo appInfo = null;
        try {
            appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            throw new Error(e);
        }
        if (!appInfo.metaData.containsKey("QQ_APPID")){
            throw new Error("meta-data QQ_APPID not found in AndroidManifest.xml");
        }
        this.appId = appInfo.metaData.get("QQ_APPID").toString();
    }

    @Override
    public void initialize() {
        super.initialize();

        if (api == null) {
            api = Tencent.createInstance(appId, getReactApplicationContext().getApplicationContext());
        }
        getReactApplicationContext().addActivityEventListener(this);
    }

    @Override
    public void onCatalystInstanceDestroy() {

        if (api != null){
            api = null;
        }
        getReactApplicationContext().removeActivityEventListener(this);

        super.onCatalystInstanceDestroy();
    }

    @Override
    public String getName() {
        return "RCTQQAPI";
    }

    @ReactMethod
    public void isQQInstalled(Promise promise) {
        if (api.isSupportSSOLogin(getCurrentActivity())) {
            promise.resolve(true);
        }
        else {
            promise.reject("not installed");
        }
    }

    @ReactMethod
    public void isQQSupportApi(Promise promise) {
        if (api.isSupportSSOLogin(getCurrentActivity())) {
            promise.resolve(true);
        }
        else {
            promise.reject("not support");
        }
    }

    @ReactMethod
    public void login(String scopes, Promise promise){
        this.isLogin = true;
        if (!api.isSessionValid()){
            api.login(getCurrentActivity(), scopes == null ? "get_simple_userinfo" : scopes, this);
            promise.resolve(null);
        } else {
            promise.reject(INVOKE_FAILED);
        }
    }

    @ReactMethod
    public void shareToQQ(ReadableMap data, Promise promise){
        this._shareToQQ(data, 0);
        promise.resolve(null);
    }

    @ReactMethod
    public void shareToQzone(ReadableMap data, Promise promise){
        this._shareToQQ(data, 1);
        promise.resolve(null);
    }

    private void _shareToQQ(ReadableMap data, int scene) {
        this.isLogin = false;
        Bundle bundle = new Bundle();
        if (data.hasKey(RCTQQShareTitle)){
            bundle.putString(QQShare.SHARE_TO_QQ_TITLE, data.getString(RCTQQShareTitle));
        }
        if (data.hasKey(RCTQQShareDescription)){
            bundle.putString(QQShare.SHARE_TO_QQ_SUMMARY, data.getString(RCTQQShareDescription));
        }
        if (data.hasKey(RCTQQShareWebpageUrl)){
            bundle.putString(QQShare.SHARE_TO_QQ_TARGET_URL, data.getString(RCTQQShareWebpageUrl));
        }
        if (data.hasKey(RCTQQShareImageUrl)){
            bundle.putString(QQShare.SHARE_TO_QQ_IMAGE_URL, data.getString(RCTQQShareImageUrl));
        }
        if (data.hasKey("appName")){
            bundle.putString(QQShare.SHARE_TO_QQ_APP_NAME, data.getString("appName"));
        }

        String type = RCTQQShareTypeNews;
        if (data.hasKey(RCTQQShareType)) {
            type = data.getString(RCTQQShareType);
        }

        if (type.equals(RCTQQShareTypeNews)){
            bundle.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_DEFAULT);
        } else if (type.equals(RCTQQShareTypeImageBase64) || type.equals(RCTQQShareTypeImage)){
            this.shareImage(data);
            return;
//            String image =data.getString(RCTQQShareImageUrl);
//            image = processImage(image);
//            bundle.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_IMAGE);
//            bundle.putString(QQShare.SHARE_TO_QQ_IMAGE_LOCAL_URL,image);
        } else if (type.equals(RCTQQShareTypeAudio)) {
            bundle.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_AUDIO);
            if (data.hasKey("flashUrl")){
                bundle.putString(QQShare.SHARE_TO_QQ_AUDIO_URL, data.getString("flashUrl"));
            }
        } else if (type.equals("app")){
            bundle.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_APP);
        }

        Log.e("QQShare", bundle.toString());

        if (scene == 0 ) {
            // Share to QQ.
            bundle.putInt(QQShare.SHARE_TO_QQ_EXT_INT, QQShare.SHARE_TO_QQ_FLAG_QZONE_ITEM_HIDE);
            api.shareToQQ(getCurrentActivity(), bundle, this);
        }
        else if (scene == 1) {
            // Share to Qzone.
            bundle.putInt(QQShare.SHARE_TO_QQ_EXT_INT, QQShare.SHARE_TO_QQ_FLAG_QZONE_AUTO_OPEN);
            api.shareToQQ(getCurrentActivity(), bundle, this);
        }
    }

    private String _getType() {
        return (this.isLogin?"QQAuthorizeResponse":"QQShareResponse");
    }

    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        Tencent.onActivityResultData(requestCode, resultCode, data, this);
    }

    public void onNewIntent(Intent intent){

    }

    @Override
    public void onComplete(Object o) {

        WritableMap resultMap = Arguments.createMap();

        if (isLogin) {
            resultMap.putString("type", "QQAuthorizeResponse");
            try {
                JSONObject obj = (JSONObject) (o);
                resultMap.putInt("errCode", 0);
                resultMap.putString("openid", obj.getString(Constants.PARAM_OPEN_ID));
                resultMap.putString("access_token", obj.getString(Constants.PARAM_ACCESS_TOKEN));
                resultMap.putString("oauth_consumer_key", this.appId);
                resultMap.putDouble("expires_in", (new Date().getTime() + obj.getLong(Constants.PARAM_EXPIRES_IN)));
            } catch (Exception e){
                WritableMap map = Arguments.createMap();
                map.putInt("errCode", Constants.ERROR_UNKNOWN);
                map.putString("errMsg", e.getLocalizedMessage());

                getReactApplicationContext()
                        .getJSModule(RCTNativeAppEventEmitter.class)
                        .emit("QQ_Resp", map);
            }
        } else {
            resultMap.putString("type", "QQShareResponse");
            resultMap.putInt("errCode", SHARE_RESULT_CODE_SUCCESSFUL);
            resultMap.putString("message", "Share successfully.");
        }

        this.resolvePromise(resultMap);
    }

    @Override
    public void onError(UiError uiError) {
        WritableMap resultMap = Arguments.createMap();
        resultMap.putInt("errCode", SHARE_RESULT_CODE_FAILED);
        resultMap.putString("message", "Share failed." + uiError.errorDetail);

        this.resolvePromise(resultMap);
    }

    @Override
    public void onCancel() {
        WritableMap resultMap = Arguments.createMap();
        resultMap.putInt("errCode", SHARE_RESULT_CODE_CANCEL);
        resultMap.putString("message", "Share canceled.");

        this.resolvePromise(resultMap);
    }

    private void resolvePromise(ReadableMap resultMap) {
        getReactApplicationContext()
                .getJSModule(RCTNativeAppEventEmitter.class)
                .emit("QQ_Resp", resultMap);

    }


    public void shareImage( ReadableMap data) {
        String image = "",title = "",description="";

        if (data.hasKey(RCTQQShareTitle)){
            title = data.getString(RCTQQShareTitle);
        }
        if (data.hasKey(RCTQQShareImageUrl)){
            image =  data.getString(RCTQQShareImageUrl);
        }
        if (data.hasKey(RCTQQShareDescription)){
            description = data.getString(RCTQQShareDescription);
        }

        final Activity currentActivity = getCurrentActivity();
        Log.d("图片地址",image);
        if (null == currentActivity) {
            return;
        }
        image = processImage(image);
        final Bundle params = new Bundle();
        params.putInt(QQShare.SHARE_TO_QQ_KEY_TYPE, QQShare.SHARE_TO_QQ_TYPE_IMAGE);
        params.putString(QQShare.SHARE_TO_QQ_IMAGE_LOCAL_URL,image);
        params.putString(QQShare.SHARE_TO_QQ_TITLE, title);
        params.putString(QQShare.SHARE_TO_QQ_SUMMARY, description);

        Log.e(TAG," params:: "+params.toString());

        Runnable qqRunnable = new Runnable() {
            @Override
            public void run() {
                api.shareToQQ(currentActivity,params,QQModule.this);
            }
        };
        UiThreadUtil.runOnUiThread(qqRunnable);

    }


    /**
     * 图片处理
     * @param image
     * @return
     */
    private String processImage(String image) {
        if (TextUtils.isEmpty(image)) {
            return "";
        }
        if(URLUtil.isHttpUrl(image) || URLUtil.isHttpsUrl(image)) {
            return saveBytesToFile(getBytesFromURL(image), getExtension(image));
        } else if (isBase64(image)) {
            return saveBitmapToFile(decodeBase64ToBitmap(image));
        } else if (URLUtil.isFileUrl(image) || image.startsWith("/") ){
            File file = new File(image);
            return file.getAbsolutePath();
        } else if(URLUtil.isContentUrl(image)) {
            return saveBitmapToFile(getBitmapFromUri(Uri.parse(image)));
        } else {
            return saveBitmapToFile(BitmapFactory.decodeResource(getReactApplicationContext().getResources(),getDrawableFileID(image)));
        }
    }



    /**
     * 获取Drawble资源的文件ID
     * @param imageName
     * @return
     */
    private int getDrawableFileID(String imageName) {
        ResourceDrawableIdHelper sResourceDrawableIdHelper = ResourceDrawableIdHelper.getInstance();
        int id = sResourceDrawableIdHelper.getResourceDrawableId(getReactApplicationContext(),imageName);
        return id;
    }

    /**
     * 检查图片字符串是不是Base64
     * @param image
     * @return
     */
    private boolean isBase64(String image) {
        try {
            byte[] decodedString = Base64.decode(image, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            if (bitmap == null) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    /**
     * 根据图片的URL转化成 byte[]
     * @param src
     * @return
     */
    private static byte[] getBytesFromURL(String src) {
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            byte[] b = getBytes(input);
            return b;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static byte[] getBytes(InputStream inputStream) throws Exception {
        byte[] b = new byte[1024];
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int len = -1;
        while ((len = inputStream.read(b)) != -1) {
            byteArrayOutputStream.write(b, 0, len);
        }
        byteArrayOutputStream.close();
        inputStream.close();
        return byteArrayOutputStream.toByteArray();
    }

    /**
     * 获取链接指向文件后缀
     *
     * @param src
     * @return
     */
    public static String getExtension(String src) {
        String extension = null;
        try {
            URL url = new URL(src);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            String contentType = connection.getContentType();
            extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return extension;
    }

    /**
     * 将Base64解码成Bitmap
     * @param Base64String
     * @return
     */
    private Bitmap decodeBase64ToBitmap(String Base64String) {
        byte[] decode = Base64.decode(Base64String,Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(decode, 0, decode.length);
        return  bitmap;
    }

    /**
     * 根据uri生成Bitmap
     * @param uri
     * @return
     */
    private Bitmap getBitmapFromUri(Uri uri) {
        try{
            InputStream inStream = this.getCurrentActivity().getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(inStream);
            return  bitmap;
        }catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        }
        return null;
    }

    /**
     * 将bitmap 保存成文件
     * @param bitmap
     * @return
     */
    private String saveBitmapToFile(Bitmap bitmap) {

        Bitmap  bm = BitmapFactory.decodeResource( getReactApplicationContext().getResources(),R.drawable.share);
        Bitmap mergeBitmap = mergeBitmap(bitmap,bm);

        File pictureFile = getOutputMediaFile();
        if (pictureFile == null) {
            return null;
        }
        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            mergeBitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }
        return pictureFile.getAbsolutePath();

    }


    /**
     * 将 byte[] 保存成文件
     * @param bytes 图片内容
     * @param ext 扩展名
     * @return
     */
    private String saveBytesToFile(byte[] bytes, String ext) {
        File pictureFile = getOutputMediaFile(ext);
        if (pictureFile == null) {
            return null;
        }
        try {
            FileOutputStream fos = new FileOutputStream(pictureFile);
            fos.write(bytes);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }
        return pictureFile.getAbsolutePath();
    }

    /**
     * 生成文件用来存储图片
     * @return
     */
    private File getOutputMediaFile(){
        return getOutputMediaFile("jpg");
    }

    private File getOutputMediaFile(String ext){
        ext = ext != null ? ext : "jpg";
        File mediaStorageDir = getCurrentActivity().getExternalCacheDir();
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                return null;
            }
        }
        String timeStamp = new SimpleDateFormat("ddMMyyyy_HHmm").format(new Date());
        File mediaFile;
        String mImageName="RN_"+ timeStamp +"." + ext;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);
        Log.d("path is",mediaFile.getPath());
        return mediaFile;
    }

    /**
     * @return 拼接图片
     */
    private Bitmap mergeBitmap(Bitmap firstBitmap, Bitmap secondBitmap) {

        int width =firstBitmap.getWidth();
        int width2 = secondBitmap.getWidth();
        int height2 = secondBitmap.getHeight();

        // 对share图片缩放处理
        Matrix matrix = new Matrix();
        float scale = ((float) width) / width2;
        matrix.setScale(scale, scale);
        Bitmap newSecondBitmap = Bitmap.createBitmap(secondBitmap, 0, 0, width2,
                height2, matrix, true);

        //拼接图片
        int height = firstBitmap.getHeight() + newSecondBitmap.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height,firstBitmap.getConfig());
        Canvas canvas = new Canvas(result);
        canvas.drawBitmap(firstBitmap, 0, 0, null);
        canvas.drawBitmap(newSecondBitmap,0, firstBitmap.getHeight(), null);
        return result;
    }
}
