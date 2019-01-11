package com.liuwei.audiodemo;

/**
 * @Author: liuwei
 * @Create: 2019/1/11 11:40
 * @Description:
 */
public class OpenGLPlay {
    static {
        System.loadLibrary("native-lib");
    }
    public native int play();
}
