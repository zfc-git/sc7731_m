package com.android.ims;

import android.os.Message;

public interface ImsUtInterfaceEx {

    public void setCallForwardingOption(int serviceId, int commandInterfaceCFAction,
            int commandInterfaceCFReason,int serviceClass, String dialingNumber,
            int timerSeconds, String ruleSet, Message onComplete);

    public void getCallForwardingOption(int serviceId, int commandInterfaceCFReason, int serviceClass,
            String ruleSet, Message onComplete);
}
