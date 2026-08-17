package com.freshmarket.address.exception;

import com.freshmarket.common.exception.BusinessException;

public class AddressException extends BusinessException {

    public AddressException(AddressErrorCode errorCode) {
        super(errorCode);
    }

    public AddressException(AddressErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
