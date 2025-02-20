package com.android.internal.telephony;

import com.android.internal.telephony.CallForwardInfo;
import android.telephony.PhoneNumberUtils;

public class CallForwardInfoEx extends CallForwardInfo{
    public String          ruleset;      /* "ruleset" from TS 27.007 */
    public int             numberType;  /* "ruleset" from TS 27.007 */

    @Override
    public String toString() {
        return super.toString()
                + "  numberType:" + numberType
                + "  ruleset:"+ruleset;
    }
}
