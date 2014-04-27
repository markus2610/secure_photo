package com.sckftr.android.securephoto.data;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.ResultReceiver;

import com.sckftr.android.securephoto.AppConst;
import com.sckftr.android.securephoto.Application;
import com.sckftr.android.securephoto.Application_;
import com.sckftr.android.securephoto.contract.Contracts;
import com.sckftr.android.securephoto.db.Cryptonite;
import com.sckftr.android.securephoto.db.Image;
import com.sckftr.android.securephoto.processor.Crypto;
import com.sckftr.android.utils.Procedure;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import by.deniotokiari.core.utils.ContractUtils;
import by.deniotokiari.core.utils.IOUtils;

public class DataApi implements AppConst {

    private static DataApi instance;

    private void unlockFile(Cryptonite item) {
        FileInputStream stream = null;
        FileOutputStream fileOutputStream = null;
        try {
            stream = new FileInputStream(item.getUri());
            byte[] buffer = new byte[stream.available()];
            stream.read(buffer);
            byte[] decrypted = Crypto.decrypt(buffer, item.getKey());
            fileOutputStream = new FileOutputStream(item.getUri());
            fileOutputStream.write(decrypted);
        } catch (FileNotFoundException e) {
            Log.e("unlock", item.getUri(), e);
        } catch (IOException e) {
            Log.e("unlock", item.getUri(), e);
        } finally {
            IOUtils.closeStream(stream);
            IOUtils.closeStream(fileOutputStream);
        }
        API.get().getApplicationContext().getContentResolver()
                .delete(ContractUtils.getUri(Contracts.ImageContract.class),
                        Contracts.ImageContract.URI + " = '" + item.getUri() + "'", null);

    }

    public static DataApi instance() {
        if (instance == null) instance = new DataApi();
        return instance;
    }

    private enum CommandName {
        unlockFile
    }

    protected static class DataAsyncEnforcerService extends IntentService {
        public static final String PARAM_IN_COMMAND_NAME = "icommand";
        public static final String PARAM_IN_DATA = "idata";
        public static final String PARAM_IN_CALLBACK = "icallback";
        public static final String PARAM_OUT_MSG = "omsg";

        public DataAsyncEnforcerService() {
            super("DataAsyncEnforcerService");
        }

        @Override
        protected void onHandleIntent(Intent intent) {
            final CommandName commandName = (CommandName) intent.getSerializableExtra(PARAM_IN_COMMAND_NAME);
            final ResultReceiver receiver = intent.getParcelableExtra(PARAM_IN_CALLBACK);
            Bundle resultingBundle = null;

            switch (commandName) {
                case unlockFile:
                    Image image = intent.getParcelableExtra(PARAM_IN_DATA);
                    API.data().unlockFile(image);
                    break;
//                case addLocationEntry:
//                    LocationInfo locationInfo = intent.getParcelableExtra(PARAM_IN_DATA);
//                    resultingBundle = createSingleEntyBundle(API.data().addLocationEntry(application, locationInfo));
//                    break;

            }

            //send results if we have receiver
            if (receiver != null) {
                receiver.send(0, resultingBundle);
            }
        }

        private Bundle createSingleEntryBundle(Serializable value) {
            Bundle resultingBundle = new Bundle(1);
            resultingBundle.putSerializable(PARAM_OUT_MSG, value);
            return resultingBundle;
        }

        private Bundle createSingleEntryBundle(Parcelable value) {
            Bundle resultingBundle = new Bundle(1);
            resultingBundle.putParcelable(PARAM_OUT_MSG, value);
            return resultingBundle;
        }

    }

    public void uncryptonize(Cryptonite cryptonite, Procedure<? extends Object> callback){
        final Intent intent = createBaseIntentForAsyncEnforcer(CommandName.unlockFile, createResultReceiver(callback));
        intent.putExtra(DataAsyncEnforcerService.PARAM_IN_DATA, cryptonite);
        dispatchServiceCall(intent);
    }

    private <T extends Serializable> Intent createBaseIntentForAsyncEnforcer(final CommandName commandName, final ResultReceiver resultReceiver) {
        Application app = Application_.getInstance();
        Intent msgIntent = new Intent(app, DataAsyncEnforcerService.class);
        msgIntent.putExtra(DataAsyncEnforcerService.PARAM_IN_COMMAND_NAME, commandName);
        if (resultReceiver != null) {
            msgIntent.putExtra(DataAsyncEnforcerService.PARAM_IN_CALLBACK, resultReceiver);
        }
        return msgIntent;
    }

    private void dispatchServiceCall(final Intent intent) {
        Application_.getInstance().startService(intent);
    }


    private <T> ResultReceiver createResultReceiver(final Procedure<T> callback) {
        if (callback == null) {
            return null;
        }

        return new ResultReceiver(null) {

            @Override
            protected void onReceiveResult(int resultCode, Bundle msg) {
                Object result;
                if (isOutputParcelable(callback)) {
                    result = msg.getParcelable(DataAsyncEnforcerService.PARAM_OUT_MSG);
                } else {
                    result = msg.getSerializable(DataAsyncEnforcerService.PARAM_OUT_MSG);
                }
                callback.apply((T) result);
            }
        };
    }

    private <T> boolean isOutputParcelable(final Procedure<T> callback) {
        Type[] types = callback.getClass().getGenericInterfaces();
        if (types.length == 0 || !(types[0] instanceof ParameterizedType)) {
            return false;
        }
        Type[] argumentsTypes = ((ParameterizedType) types[0]).getActualTypeArguments();
        return !(argumentsTypes.length == 0 || !(argumentsTypes[0] instanceof Class)) && Parcelable.class.isAssignableFrom((Class<?>) argumentsTypes[0]);
    }
}