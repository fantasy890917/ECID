package com.huaqin.ecidparser;

import android.content.Context;
import android.util.Log;

import com.huaqin.ecidparser.utils.CAItem;
import com.huaqin.ecidparser.utils.LgeMccMncSimInfo;
import com.huaqin.ecidparser.utils.ProfileData;
import com.huaqin.ecidparser.utils.Utils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Iterator;
import java.io.IOException;
public class LgeMmsConfigParser extends GeneralProfileParser {

    private static final String TAG = Utils.APP+LgeMmsConfigParser.class.getSimpleName();;
    public LgeMmsConfigParser(Context context) {
        super(context);
    }

    @Override
    protected ProfileData getMatchedProfile(XmlPullParser parser, LgeMccMncSimInfo simInfo, HashMap map) {
        Log.d(TAG, "getMatchedProfile");
        ProfileData commonProfile = null;
        ProfileData validProfile = null;
        boolean found;
        MatchedProfile profile = new MatchedProfile();

        if (parser == null) {
            return null;
        }

        try {
            // find a "<profiles>" element
            beginDocument(parser, ELEMENT_NAME_PROFILES);

            while (true) {
                // find a "<profiles>" element
                if (ELEMENT_NAME_PROFILES.equals(parser.getName())) {
                    nextElement(parser);
                }
                // find a "<profile>" element
                if (ELEMENT_NAME_PROFILE.equals(parser.getName())) {
                    nextElement(parser);    // find a "<siminfo>" element or <FeatureSet>
                }
                if (parser.getEventType() == XmlPullParser.END_DOCUMENT) {
                    break;
                }
                // find a "<siminfo>" element
                if (ELEMENT_NAME_SIMINFO.equals(parser.getName())) {
                    found = getValidProfile(profile, parser, simInfo);
                    // test code , if sim info is null, use default profile (need to place default profile at the top of the profiles, the fastest way)
                    // when bestMatchedProfile was found
                    if (profile.mDefaultProfile != null
                            || profile.mBestMatchedProfile != null) {
                        if (VDBG) {
                            Log.v(TAG, "[getMatchedProfile] sim info : " + simInfo + "bestMatchedProfile" + profile.mBestMatchedProfile);
                        }
                        break;
                    }

                    // we didn't parse this element
                    if (!found) {
                        skipCurrentElement(parser);

                        if (DBG) {
                            Log.d(TAG, "[getMatchedProfile] skipCurrentElement");
                        }
                    }
                } else if (ELEMENT_NAME_COMMONPROFILE.equals(parser.getName())) {
                    // find a "<CommonProfile>" element
                    commonProfile = readProfile(parser);
                } else {
                    throw new XmlPullParserException("Unexpected tag: found " + parser.getName());
                }

            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        validProfile = profile.mBestMatchedProfile != null ? profile.mBestMatchedProfile :
                profile.mCandidateProfile != null ? profile.mCandidateProfile : profile.mDefaultProfile;
        return mergeProfile(commonProfile, validProfile, map);

    }


    protected void changeGpriValueFromLGE(HashMap hashmap, ProfileData data) {
        Log.d(TAG,"changeGpriValueFromLGE");
        HashMap<String, String> matchmap = new HashMap<String, String>();
        matchmap.put("Message@SMS_Delivery_Report", "sms_dr");
        matchmap.put("Message@SMS_Number", "smsc");
        matchmap.put("Message@SMS_Editable", "smsc_readonly");
        matchmap.put("Message@SMS_Unicode_characters", "sms_char");
        //matchmap.put("Message@Cell_Broadcast_Receive", "sms_sent_time_mode");
        matchmap.put("Message@SMS_Validity_Period", "sms_validity");
        matchmap.put("Message@SMS_Turn_to_MMS_when_SMS_size_is_more_than", "sms_concat");
        matchmap.put("Message@MMS_Report_Request", "mms_dr_r");
        matchmap.put("Message@SMS_Remove_SMS_templates", "sms_templates_removable");

        matchmap.put("Message@MMS_Allow_Report", "mms_dr_a");
        matchmap.put("Message@MMS_Request_reply", "mms_rr_r");
        matchmap.put("Message@MMS_Allow_reply", "mms_rr_a");
        matchmap.put("Message@MMS_Home", "auto_retr");
        matchmap.put("Message@MMS_Roaming", "auto_retr_r");
        matchmap.put("Message@MMS_MMS_Priority", "mms_priority");
        matchmap.put("Message@MMS_Validity_period", "mms_validity");
        matchmap.put("Message@MMS_Maximum_Size", "mms_size");

        matchmap.put("Message@MMS_Creation_mode", "mms_creation");
        matchmap.put("Message@MMS_Slide_Duration_(in_seconds)", "slide_dur");
        matchmap.put("Message@MMS_Advertisement", "recv_adv");
        matchmap.put("Message@Cell_Broadcast_Receive", "cb_on");
        matchmap.put("Internet@Receive_push_message", "push_on");
        //matchmap.put("Message@MMS_Maximum_Size", "del_old");

        Iterator<String> key = matchmap.keySet().iterator();

        while (key.hasNext()) {
            String tag = key.next();
            String matchString_Value = (String) matchmap.get(tag);
            String value = ((NameValueProfile)data).getValue(matchString_Value);
            if (value != null) {
                Log.d(TAG,"match:"+matchString_Value+" value:"+value);
                if ("smsc_readonly".equals(matchString_Value)) {
                    value = "1".equals(value) ? "0" : "1";
                }
                hashmap.put(tag, value);
            }
        }
    }

    @Override
    protected boolean currentElemntShouldBeSkiped(XmlPullParser parser) {
        return ELEMENT_NAME_SIMINFO.equals(parser.getName()) ||
                ELEMENT_NAME_FEATURESET.equals(parser.getName());
    }

    @Override
    protected boolean currentElemntHasValidTag(XmlPullParser parser) {
        return parser.getName() != null
                &&(!parser.getName().equals(ELEMENT_NAME_PROFILE));
    }

    @Override
    protected String getValidParserTagName(XmlPullParser parser) {
        return parser.getName();
    }

}


