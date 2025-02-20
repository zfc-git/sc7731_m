package com.android.internal.telephony.uicc;

public class IccPBForEncodeException extends Exception {
    public IccPBForEncodeException() {
        super();
    }

    public IccPBForEncodeException(String s) {
        super(s);
    }

    public IccPBForEncodeException(char c) {
        super("Unencodable char: '" + c + "'");
    }
}

