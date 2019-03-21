package com.fxyan.draco.exception;

/**
 * @author fxYan
 */
public final class DracoException
        extends Exception {

    enum Status {
        DOWNLOAD_ERROR("下载失败"),
        DECODE_ERROR("解析失败");

        String message;

        Status(String _message) {
            this.message = _message;
        }
    }

}
