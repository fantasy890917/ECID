package com.huaqin.ecidparser;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.huaqin.ecidparser.utils.GeneralParserAttribute;
import com.huaqin.ecidparser.utils.SIMInfoConstants;
import com.huaqin.ecidparser.utils.Utils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;
import com.huaqin.ecidparser.utils.*;
/**
 * Created by shiguibiao on 16-8-4.
 */

public class GeneralProfileParser  implements GeneralParserAttribute{

    public static final String TAG = Utils.APP;
    public static final boolean DBG = true;
    public static final boolean VDBG = true;

    public Context mContext;
    public FileReader in = null;
    public XmlPullParserFactory factory;
    public XmlPullParser parser = null;
    public ProfileData parseData = null;


    public final static int FLAGS_NO_MATCH = 0;
    public final static int FLAGS_MATCH_FOR_NO_PRI = 1 << 0; // 1
    public final static int FLAGS_MATCH_IMSI = 1 << 2; // 4
    public final static int FLAGS_MATCH_GID = 1 << 3; // 8
    public final static int FLAGS_MATCH_SPN = 1 << 4;
    public final static int FLAGS_MATCH_MNC = 1 << 5;
    public final static int FLAGS_MATCH_MCC = 1 << 6;

    public static boolean mXmlMatchingData = false;

    public static int mPhoneId = 0;
    public GeneralProfileParser(Context context) {
        mContext = context;

    }



    protected class MatchedProfile {
        public ProfileData mBestMatchedProfile;
        public ProfileData mCandidateProfile;
        public ProfileData mDefaultProfile;
    }

    public static class NameValueProfile extends ProfileData {
        private HashMap<String, String> mNameValueMap = new HashMap<String, String>();

        public void setValue(String key, String value) {
            mNameValueMap.put(key, value);
        }

        public String getValue(String key) {
            return mNameValueMap.get(key);
        }

        public String getValue(String key, String defaultValue) {
            if (!mNameValueMap.containsKey(key)) {
                return defaultValue;
            }

            return mNameValueMap.get(key);
        }
        public void remove(String key) {
            mNameValueMap.remove(key);
        }


        public HashMap<String, String> getValueMap() {
            return mNameValueMap;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            Set<String> set = mNameValueMap.keySet();
            Iterator<String> keys = set.iterator();

            while (keys.hasNext()) {
                String key = keys.next();
                sb.append(key + "=" + mNameValueMap.get(key) + "\n");
            }


            if (sb == null) {
                return null;
            }

            return sb.toString();
        }
    }




    /**
     * This method will be called when needs to merge commonProfile and matchedProfile
     *
     * @param commonProfile which has default attribute
     * @param matchedProfile matched
     * @return The ProfileData merged
     */
//<2015/12/15-junam.hwang, [D5][PORTING][COMMON][FLEX2][][] modify the gpri mapping
    protected ProfileData mergeProfile(ProfileData commonProfile, ProfileData matchedProfile,
                                       HashMap<String, String> map) {
        Log.d(TAG,"mergeProfile-----------------");
        if(matchedProfile == null){
            return null;
        }
        Log.d(TAG,"matchedProfile-----------------");
        NameValueProfile cp = (NameValueProfile)commonProfile;
        NameValueProfile mp = (NameValueProfile)matchedProfile;

        Set<String> set;
        Iterator<String> keys;

        if (cp != null) {
            // Set Common Profile
            set = cp.mNameValueMap.keySet();
            keys = set.iterator();

            while (keys.hasNext()) {
                String key = keys.next();
                if (!mp.mNameValueMap.containsKey(key)) {
                    mp.mNameValueMap.put(key, cp.getValue(key));
                }
            }
        }
        Log.d(TAG,"need changeGpriValueFromLGE-----------------");
        changeGpriValueFromLGE(map, mp);
        return mp;
    }


    public boolean getValidProfile(MatchedProfile profile, XmlPullParser parser, LgeMccMncSimInfo simInfo) {
        ProfileData p = null;
        boolean found = false;

        String mccValue = parser.getAttributeValue(null, SIMInfoConstants.ATTR_NAME_MCC);
        String mncValue = parser.getAttributeValue(null, SIMInfoConstants.ATTR_NAME_MNC);
        String operatorValue = parser.getAttributeValue(null, SIMInfoConstants.ATTR_NAME_OPERATOR);
        String countryValue = parser.getAttributeValue(null, SIMInfoConstants.ATTR_NAME_COUNTRY);
        String gidValue = parser.getAttributeValue(null, SIMInfoConstants.ATTR_NAME_GID);
        String spnValue = parser.getAttributeValue(null, SIMInfoConstants.ATTR_NAME_SPN);
        String imsiValue = parser.getAttributeValue(null, SIMInfoConstants.ATTR_NAME_IMSI_RANGE);

        boolean isDefault = "true".equals(parser.getAttributeValue(null, SIMInfoConstants.ATTR_NAME_DEFAULT));

        if (isDefault) {
            // keep the default profile
            if (profile.mDefaultProfile == null) {
                p = setParseDataPrio("3", parser);
                profile.mDefaultProfile = p;
            }
            found = true;
            if (simInfo == null || simInfo.isNull()) {
                return found;
            }
        }

        if (DBG) {
            Log.d(TAG, "[getMatchedProfile] TAG : " + parser.getName());
            Log.d(TAG, "[getMatchedProfile] MCC : " + mccValue);
            Log.d(TAG, "[getMatchedProfile] MNC : " + mncValue);
            Log.d(TAG, "[getMatchedProfile] OPERATOR : " + operatorValue);
            Log.d(TAG, "[getMatchedProfile] COUNTRY : " + countryValue);
        }

        if (matchMccMnc(simInfo, mccValue, mncValue)) {
            // keep the first mccmnc matched profile.
            // input value to candidateProfile in case of no gid, spn & imsi
            if (profile.mCandidateProfile == null && TextUtils.isEmpty(gidValue) && TextUtils.isEmpty(spnValue) && TextUtils.isEmpty(imsiValue)) {
                if (p == null) {
                    p = setParseDataPrio("2", parser);
                }
                profile.mCandidateProfile = p;
                found = true;
            }

            if (DBG) {
                Log.d(TAG, "[getMatchedProfile] GID : " + gidValue);
                Log.d(TAG, "[getMatchedProfile] SPN : " + spnValue);
                Log.d(TAG, "[getMatchedProfile] IMSI : " + imsiValue);
            }
            if (profile.mBestMatchedProfile == null && (!TextUtils.isEmpty(gidValue) || !TextUtils.isEmpty(spnValue) || !TextUtils.isEmpty(imsiValue))) {

                if (matchExtension(simInfo, gidValue, spnValue, imsiValue)) {

                    profile.mBestMatchedProfile = setParseDataPrio("1", parser);

                    found = true;

                }
            }
        }

        return found;
    }

    public ProfileData setParseDataPrio(String prio, XmlPullParser parser) {
        ProfileData p = null;
        try {
            p = readProfile(parser);
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.d(TAG, prio + " is found");
        ((NameValueProfile)p).setValue("p", prio);

        return p;
    }

    /**
     * This method will be called whenever the parser meets &lt;Profile&gt;
     *
     * @param parser XmlPullParser
     * @return The ProfileData object
     */
    protected ProfileData readProfile(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        Log.d(TAG,"readProfile-----------------");
        NameValueProfile p = new NameValueProfile();
        int type;
        Log.d(TAG,"readProfile---name="+parser.getName()+" text="+parser.getText());
        while (currentElemntShouldBeSkiped(parser)) {
            nextElement(parser);
        }
        Log.d(TAG,"readProfile---valid Element name="+parser.getName());
        while (currentElemntHasValidTag(parser)) {

            String tag = parser.getName();
            if (DBG) {
                Log.d(TAG, "[readProfile] TAG : " + tag);
            }

            String key = getValidParserTagName(parser);
            if (key != null) {
                type = parser.next();
                if (type == XmlPullParser.TEXT) {
                    String value = parser.getText();
                    p.setValue(key, value);
                    if (DBG) {
                        Log.d(TAG, "[readProfile] KEY : " + key + ", VALUE : " + value);
                    }
                }
            }
            nextElement(parser);
        }

        return (ProfileData)p;
    }

    //should be override
    protected  boolean currentElemntShouldBeSkiped(XmlPullParser parser){
        return ELEMENT_NAME_SIMINFO.equals(parser.getName()) ||
                ELEMENT_NAME_FEATURESET.equals(parser.getName());
    }
    //should be override
    protected  boolean currentElemntHasValidTag(XmlPullParser parser){
        return ELEMENT_NAME_ITEM.equals(parser.getName());
    }
    //should be override
    protected String getValidParserTagName(XmlPullParser parser){
        return parser.getAttributeValue(null, ATTR_NAME);
    }


    protected boolean matchExtension(LgeMccMncSimInfo simInfo, String gidParsed, String spnParsed, String imsiParsed) {
        if (simInfo == null) {
            return false;
        }
        String gid = simInfo.getGid();
        String spn = simInfo.getSpn();
        String imsi = simInfo.getImsi();

        if (TextUtils.isEmpty(gid) && TextUtils.isEmpty(spn) && TextUtils.isEmpty(imsi)) {
            return false;
        }

        if (!TextUtils.isEmpty(gid) && gidParsed != null) {
            int gidLength = gid.length();
            if ("ff".equals(gid) || "00".equals(gid)) {
                if (DBG) { Log.d(TAG, "[matchExtension] invalid gid"+gid); }
                return false;
            }else if(existInTokens(gidParsed, gid, gidLength)){
                if (DBG) { Log.d(TAG, "[matchExtension] match gid"); }
                return true;
            }
        }

        if (spn != null && spnParsed != null) {
            if (spnParsed.equalsIgnoreCase(spn)) {
                if (DBG) { Log.d(TAG, "[matchExtension] match spn"); }
                return true;
            }
        }

        if (imsi != null && imsiParsed != null && imsiParsed.length() != 0) {
            if (matchImsi(imsiParsed, imsi)) {
                if (DBG) { Log.d(TAG, "[matchExtension] match imsi"); }
                return true;
            }
        }

        if (DBG) {
            Log.v(TAG, "[matchExtension] true");
        }
        return false;
    }

    /*[LGSI-TELEPHONY][sarmistha.pani][TD342174 Contact apk crash][05.06.2013][START]
     * Contacts Apk crash due to Null exception while accessing Imsi length in profileParser . At this point IMSI is null
     */
    protected boolean matchImsi(String imsiParsed, String imsi) {
        boolean match_imsi = true;
        int length;

        if (imsi.length() >=  imsiParsed.length()) {
            length = imsiParsed.length();
        } else {
            length = imsi.length();
        }

        for ( int i = 0; i < length; i++ ) {
            if (imsi.charAt(i) == 'x') {
                continue;
            } else if (imsi.charAt(i) != imsiParsed.charAt(i)) {
                match_imsi = false;
                break;
            }
        }

        return match_imsi;
    }


    protected boolean existInTokens(String string, String v, int len) {
        if (string == null || v == null) {
            return false;
        }

        int xml_length = string.length();
        int final_length;

        if (xml_length > len) {
            final_length = len;
        }
        else {
            final_length = xml_length;
        }

        if (DBG) { Log.d(TAG, "[existInTokens] final length : " + final_length); }

        String fixed_xml_gid = string.substring(0, final_length);
        String fixed_sim_gid = v.substring(0, final_length);

        if (fixed_xml_gid.equalsIgnoreCase(fixed_sim_gid)) {
            return true;
        }

        return false;
    }

    protected boolean matchMccMnc(LgeMccMncSimInfo simInfo, String mccParsed, String mncParsed) {
        if (simInfo != null) {
            String mcc = simInfo.getMcc();
            String mnc = simInfo.getMnc();

            if (!TextUtils.isEmpty(mcc) && !TextUtils.isEmpty(mnc)) {
                if (mcc.equals(mccParsed) && mnc.equals(mncParsed)) {
                    //if (TextUtils.equals(mcc, mccParsed) && TextUtils.equals(mnc, mncParsed)) {
                    if (DBG) { Log.d(TAG, "[matchMccMnc] true"); }
                    return true;
                }
            }
        }
        if (DBG) { Log.d(TAG, "[matchMccMnc] false"); }

        return false;
    }


    protected final void beginDocument(XmlPullParser parser, String firstElementName)
            throws XmlPullParserException, IOException {
        int type = parser.next();
        String name = parser.getName();
        if (type != XmlPullParser.START_TAG) {
            throw new XmlPullParserException("No start tag found");
        }

        if (!firstElementName.equals(name)) {
            throw new XmlPullParserException("Unexpected start tag: found " + name + ", expected "
                    + firstElementName);
        }
    }

    protected final void nextElement(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        int type;
        while ((type = parser.next()) != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
            ;   // do nothing
        }
    }

    protected final void skipCurrentElement(XmlPullParser parser)
            throws XmlPullParserException, IOException {

        nextElement(parser);
        if (DBG) { Log.d(TAG, "[skipCurrentElement] nextElement : " + parser.getName()); }

        if (ELEMENT_NAME_SIMINFO.equals(parser.getName()) ||
                ELEMENT_NAME_PROFILE.equals(parser.getName())) {
            return;
        }

        while ((parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (!currentElemntShouldBeSkiped(parser)) {
                nextElement(parser);
            }

            if (DBG) { Log.d(TAG, "[skipCurrentElement] currentElement : " + parser.getName()); }

            if (ELEMENT_NAME_PROFILE.equals(parser.getName())) {
                break;
            }
        }
    }

    /**
     * Gets a matched profile from a XmlPullParser
     *
     * @param parser XmlPullParser object
     * @param simInfo The SIM card information
     * @param map add to preference  eunheon.kim
     * @return The ProfileData object matched
     */
    protected ProfileData getMatchedProfile(XmlPullParser parser, LgeMccMncSimInfo simInfo, HashMap map) {return null;}
    /* */
    protected void changeGpriValueFromLGE(HashMap map,  ProfileData validProfile) {}

    public HashMap<String, String> loadLgProfile(String path, HashMap<String, String> map, LgeMccMncSimInfo siminfo)
    {
        try {
            File file = new File(path);
            in = new FileReader(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        try {
            factory = XmlPullParserFactory.newInstance();
            parser = factory.newPullParser();
            parser.setInput(in);
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        }

        ProfileData parsedData = getMatchedProfile(parser, siminfo, map);
        NameValueProfile cp = (NameValueProfile)parsedData;

        if (cp == null) {
            Log.e(TAG, "parseredData is null");
            return null;
        }
        return cp.mNameValueMap;

    };

}

