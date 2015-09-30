package com.cocosw.favor;

import android.content.SharedPreferences;
import android.text.TextUtils;

import com.f2prateek.rx.preferences.Preference;
import com.f2prateek.rx.preferences.RxSharedPreferences;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;

/**
 * <p/>
 * Created by kai on 25/09/15.
 */

class MethodInfo {

    static final boolean HAS_RX_JAVA = hasRxJavaOnClasspath();
    final Method method;
    // Method-level details
    final ResponseType responseType;
    final boolean isObservable;
    private final SharedPreferences sp;
    private final String prefix;
    boolean loaded = false;
    Type responseObjectType;
    String key;
    String[] defaultValues = new String[1];
    Object rxPref;
    private Taste taste;
    private boolean commit;
    private Type FavorType;

    MethodInfo(Method method, SharedPreferences sp, String prefix) {
        this.method = method;
        this.sp = sp;
        this.prefix = prefix;
        responseType = parseResponseType();
        isObservable = (responseType == ResponseType.OBSERVABLE);
    }

    private static Type getParameterUpperBound(ParameterizedType type) {
        Type[] types = type.getActualTypeArguments();
        for (int i = 0; i < types.length; i++) {
            Type paramType = types[i];
            if (paramType instanceof WildcardType) {
                types[i] = ((WildcardType) paramType).getUpperBounds()[0];
            }
        }
        return types[0];
    }

    private static boolean hasRxJavaOnClasspath() {
        try {
            Class.forName("com.f2prateek.rx.preferences.Preference");
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        return false;
    }

    private RuntimeException methodError(String message, Object... args) {
        if (args.length > 0) {
            message = String.format(message, args);
        }
        return new IllegalArgumentException(
                method.getDeclaringClass().getSimpleName() + "." + method.getName() + ": " + message);
    }

    private RuntimeException parameterError(int index, String message, Object... args) {
        return methodError(message + " (parameter #" + (index + 1) + ")", args);
    }

    synchronized void init() {
        if (loaded) return;
        parseMethodAnnotations();
        loaded = true;
    }

    private void parseMethodAnnotations() {
        for (Annotation methodAnnotation : method.getAnnotations()) {
            Class<? extends Annotation> annotationType = methodAnnotation.annotationType();

            if (annotationType == Favor.class) {
                key = ((Favor) methodAnnotation).value();
                if (key.trim().length() == 0) {
                    key = getKeyFromMethod(method);
                }
                if (!TextUtils.isEmpty(prefix)) {
                    key = prefix + key;
                }
            } else if (annotationType == Default.class) {
                defaultValues = ((Default) methodAnnotation).value();
            } else if (annotationType == Commit.class) {
                commit = true;
            }
        }

        if (FavorType == String.class) {
            taste = new Taste.StringTaste(sp, key, defaultValues);
        } else if (FavorType == boolean.class) {
            taste = new Taste.BoolTaste(sp, key, defaultValues);
        } else if (FavorType == int.class) {
            taste = new Taste.IntTaste(sp, key, defaultValues);
        } else if (FavorType == float.class) {
            taste = new Taste.FloatTaste(sp, key, defaultValues);
        } else if (FavorType == long.class) {
            taste = new Taste.LongTaste(sp,key,defaultValues);
        } else {
            taste = new Taste.EmptyTaste(sp, key, defaultValues);
        }
    }

    private String getKeyFromMethod(Method method) {
        String value = method.getName().toLowerCase();
        if (value.startsWith("get")) return value.substring(3);
        if (value.startsWith("set")) return value.substring(3);
        return value;
    }

    private ResponseType parseResponseType() {
        Type returnType = method.getGenericReturnType();
        Type[] parameterTypes = method.getGenericParameterTypes();

        if (parameterTypes.length > 1) {
            throw methodError("%s method has more than one parameter", method.getName());
        }
        Type typeToCheck = null;

        if (parameterTypes.length > 0) {
            typeToCheck = parameterTypes[0];
        }

        boolean hasReturnType = returnType != void.class;

        if (hasReturnType) {
            Class rawReturnType = Types.getRawType(returnType);
            if (parameterTypes.length > 0) {
                throw methodError("getter method %s should not have parameter", method.getName());
            }

            if (HAS_RX_JAVA) {
                if (rawReturnType == Preference.class) {
                    RxSharedPreferences rx = RxSharedPreferences.create(sp);
                    returnType = Types.getSupertype(returnType, rawReturnType, Preference.class);

                    responseObjectType = getParameterUpperBound((ParameterizedType) returnType);
                    if (responseObjectType == String.class) {
                        rxPref = rx.getString(key, defaultValues[0]);
                    } else if (responseObjectType == int.class) {
                        rxPref = rx.getInteger(key, Integer.valueOf(defaultValues[0]));
                    } else if (responseObjectType == float.class) {
                        rxPref = rx.getFloat(key, Float.valueOf(defaultValues[0]));
                    } else if (responseObjectType == long.class) {
                        rxPref = rx.getLong(key, Long.valueOf(defaultValues[0]));
                    } else if (responseObjectType == boolean.class) {
                        rxPref = rx.getBoolean(key, Boolean.valueOf(defaultValues[0]));
                    } else {
//                        Class returnTypeClass = Types.getRawType(returnType);
//                        if (returnTypeClass == Set.class) {
//                            rxPref = rx.getStringSet(key,new HashSet<String>(defaultValues))
//                        }
                    }
                    return ResponseType.OBSERVABLE;
                }
            }
            responseObjectType = returnType;
        }
        FavorType = (hasReturnType ? returnType : typeToCheck);
        return hasReturnType ? ResponseType.OBJECT : ResponseType.VOID;
    }

    Object get() {
        return taste.get();
    }

    Object set(Object[] args) {
        if (commit)
            taste.commit(args[0]);
        else
            taste.set(args[0]);
        return null;
    }

    enum ResponseType {
        VOID,
        OBSERVABLE,
        OBJECT
    }

}