package com.ubase.uclass.util;

import android.text.TextUtils;
import android.util.Log;
import android.webkit.ConsoleMessage;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
* 로그 출력 Utility
**/
public class Logger {

    // 로그 태그
    /** 개발용 로그 태그 */
    public static final String TAG_DEV         = "UCLASS_DEV";
    /** 정보성 로그 태그 */
    public static final String TAG_INFO        = "UCLASS_INFO";
    /** 에러 로그 태그 */
    public static final String TAG_ERR         = "UCLASS_ERR";
    /** 웹 로그 태그 */
    public static final String TAG_WEB         = "UCLASS_WEB";

    /**
    * 로그 출력 레벨 코드 정의
    **/
    public enum LOG_LEVEL {
        /** 개발용 로그 */
        DEV,
        /** 정보성 로그 */
        INFO,
        /** 경고성 로그 */
        WARN,
        /** 에러 로그 */
        ERROR
    }

    /** 로그 출력 여부 */
    private static boolean isEnable;
    private static boolean isPathPrint;

    /** 로그 출력 Level */
    private static LOG_LEVEL [] mLevels;

    /**
     * 로그 출력 여부 설정
     * @param isPrint 로그 출력 여부 (true : 출력 / false : 미출력)
     */
    public static void setEnable(boolean isPrint) {
        isEnable = isPrint;
        if (isPrint) {
            // 로그 출력
            // 모든 Level 로그 출력 설정
            mLevels = LOG_LEVEL.values();
        } else {
            // 로그 미출력
            // 로그 Level 설정 초기화
            mLevels = null;
        }
    }

    /**
     * 로그 출력 여부 설정값 반환
     * @return 로그 출력 여부 (true : 출력 / false : 미출력)
     */
    public static boolean isEnable () {
        return isEnable;
    }

    public static void setPrintPath (boolean isPrintPath) {
        isPathPrint = isPrintPath;
    }

    /**
     * 로그 출력 Level 설정
     * 
     * - 로그 출력 여부가 '미출력 (false)' 로 설정되어 있는 경우 무시 
     * @param levels 로그 출력 Level 리스트 (LOG_LEVEL [])
     */
    public static void setLevel(LOG_LEVEL... levels) {
        if (!isEnable () || levels == null)
            return;

        mLevels = levels;
    }

    /**
     * 로그 출력 Level 설정
     * 
     * - 로그 출력 여부가 '미출력 (false)' 로 설정되어 있는 경우 무시 
     * @param levels 로그 출력 Level 리스트 (String [], 로그 Level 코드 String)
     */
    public static void setLevel (String... levels) {
        if (!isEnable () || levels == null)
            return;

        List<LOG_LEVEL> logLevel = new ArrayList<>();
        for (String level : levels) {
            try {
                logLevel.add(LOG_LEVEL.valueOf(level));
            } catch (IllegalArgumentException e) {
                // 정의되지 않은 Level
                Logger.error(e);
            }
        }
        mLevels = logLevel.toArray(new LOG_LEVEL[logLevel.size()]);
    }

    /**
     * 로그 출력 Level 반환
     * @return 로그 출력 Level 리스트
     */
    public static LOG_LEVEL [] getLevel () {
        return mLevels;
    }

    /**
     * 개발용 로그 출력
     * @param value 로그 메시지
     */
    public static void dev (String value) {
        printLog(LOG_LEVEL.DEV, TAG_DEV, getCallStack(), value);
    }

    /**
     * 정보성 로그 출력
     * @param value 로그 메시지
     */
    public static void info (String value) {
        printLog(LOG_LEVEL.INFO, TAG_INFO, getCallStack(), value);
    }

    /**
     * 오류 로그 출력
     * @param value 오루 메시지
     */
    public static void error (String value) {
        printLog(LOG_LEVEL.ERROR, TAG_ERR, getCallStack(), value);
    }

    /**
     * 오류 로그 출력
     * @param e Exception
     */
    public static void error (Exception e) {
        String msg = e.getClass() + "[ " + e.getMessage() + " ]" + " >>> ";
        StackTraceElement [] traceElements = e.getStackTrace();
        for (StackTraceElement traceElement : traceElements) {
            msg += traceElement.toString() + "\n";
        }

        printLog(LOG_LEVEL.ERROR, TAG_ERR, getCallStack(), msg);
    }

    /**
     * 오류 로그 출력
     * @param e Exception
     */
    public static void error (Throwable e) {
        String msg = e.getClass() + "[ " + e.getMessage() + " ]" + " >>> ";
        StackTraceElement [] traceElements = e.getStackTrace();
        for (StackTraceElement traceElement : traceElements) {
            msg += traceElement.toString() + "\n";
        }

        printLog(LOG_LEVEL.ERROR, TAG_ERR, getCallStack(), msg);
    }

    /**
     * 웹 로그 출력
     * @param consoleMessage 웹 ConsoleMessage
     */
    public static void web (ConsoleMessage consoleMessage) {
        // 로그를 출력한 앱 위치정보
        String[] appCallStack = getCallStack();
        // 로그를 출력한 웹 위치정보
        String[] webCallStack = { consoleMessage.sourceId(), String.valueOf(consoleMessage.lineNumber()) };
        String[] callStack;
        if (appCallStack == null) {
            callStack = new String[webCallStack.length];
            System.arraycopy(webCallStack, 0, callStack, 0, webCallStack.length);
        } else {
            callStack = new String[appCallStack.length + webCallStack.length];
            System.arraycopy(appCallStack, 0, callStack, 0, appCallStack.length);
            System.arraycopy(webCallStack, 0, callStack, appCallStack.length, webCallStack.length);
        }

        if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.LOG) {
            printLog(LOG_LEVEL.DEV, TAG_WEB, callStack, consoleMessage.message());
        }
        else if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.TIP) {
            printLog(LOG_LEVEL.INFO, TAG_WEB, callStack, consoleMessage.message());
        }
        else if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.WARNING) {
            printLog(LOG_LEVEL.WARN, TAG_WEB, callStack, consoleMessage.message());
        }
        else if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
            printLog(LOG_LEVEL.ERROR, TAG_WEB, callStack, consoleMessage.message());
        }
    }

    /**
     * 로그를 호출한 위치 정보 반환
     * @return 로그를 호출한 위치 정보
     */
    private static String[] getCallStack() {
        // 메서드를 호출한 경로에 대한 정보
        // index 0 : 현재 클래스 / 메소드
        // index 1 : 호출한 클래스 / 메소드 (내부에서 호출)
        // index 2 : 실제로 로그를 호출한 클래스 / 메소드
//            System.out.print("[ " + i + " ] 클래스 - " + a[i].getClassName());
//            System.out.print(", 메소드 - "+stackElms[i].getMethodName());
//            System.out.print(", 라인 - "+stackElms[i].getLineNumber());
//            System.out.print(", 파일 - "+stackElms[i].getFileName());
//            System.out.println();
        if (isPathPrint) {
            Throwable throwable = new Throwable();
            StackTraceElement[] stackElms = throwable.getStackTrace ();
            String [] callStack = new String[3];
            for(int i = 0; i < stackElms.length; i++) {
//            System.out.print("[ " + i + " ] 클래스 - " + stackElms[i].getClassName());
//            System.out.print(", 메소드 - "+stackElms[i].getMethodName());
//            System.out.print(", 라인 - "+stackElms[i].getLineNumber());
//            System.out.print(", 파일 - "+stackElms[i].getFileName());
//            System.out.println();

                if (!Logger.class.getName().equals(stackElms[i].getClassName())) {
                    callStack[0] = stackElms[i].getClassName();
                    callStack[1] = stackElms[i].getMethodName();
                    callStack[2] = String.valueOf(stackElms[i].getLineNumber());
                    break;
                }
            }

        /*

            Hello Android
            Construct TEST
         */
            // [클래스명-메소드명-라인]
            // 생성자의 경우 <init>
            return callStack;
        } else {
            return null;
        }
    }

    /**
     * 로그 출력 설정 검사
     * @param printLevel 출력하고자 하는 로그 Level
     * @return 로그 출력 가능 여부 (true : 출력 가능 / false : 출력 불가능)
     */
    private static boolean checkSetting (LOG_LEVEL printLevel) {
        if (isEnable () && mLevels != null) {
            for (LOG_LEVEL logLevel : mLevels) {
                if (logLevel.name().equals(printLevel.name())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 로그 출력
     * @param level 로그 출력 Level
     * @param tag 로그 태그
     * @param callStack 로그를 출력한 위치 정보
     * @param value 로그 메시지
     */
    private static void printLog (LOG_LEVEL level, String tag, String[] callStack, String value) {
        if (!checkSetting(level))
            return;

        String moreLog = "";
        String printLog;
        // 3000자가 넘을 경우 3000자까지만 프린트하고 나머지 값은 재귀함수를 호출한다.
        if (value != null && value.length() > 3000) {
            printLog = value.substring(0, 3000);
            moreLog = value.substring(3000);
            // 3000자가 넘지 않을 경우 모두 프린트한다.
        } else {
            printLog = value;
        }

        /*
        V: 상세 (가장 낮은 우선순위)
        D: 디버그
        I: 정보
        W: 경고
        E: 오류
        A: 강제 종료
         */
        if (level == LOG_LEVEL.DEV) {
            Log.d(tag, (callStack == null ? "" : Arrays.toString(callStack) + " ") + printLog);
        }
        else if (level == LOG_LEVEL.INFO) {
            Log.i(tag, (callStack == null ? "" : Arrays.toString(callStack) + " ") + printLog);
        }
        else if (level == LOG_LEVEL.WARN) {
            Log.w(tag, (callStack == null ? "" : Arrays.toString(callStack) + " ") + printLog);
        }
        else if (level == LOG_LEVEL.ERROR) {
            Log.e(tag, (callStack == null ? "" : Arrays.toString(callStack) + " ") + printLog);
        }

        if (!TextUtils.isEmpty(moreLog)) {
            // 재귀함수
            printLog(level, tag, null, moreLog);
        }
    }

    /**
     * 모든 로그 파일 삭제
     *
     * @param logFileDir 로그파일 디렉토리 경로
     * @return 삭제 성공 여부
     */
    public static boolean removeAllLogFile(File logFileDir) {
        boolean result = false;
        File[] childFileList = logFileDir.listFiles();

        if (childFileList == null) {
            result = true;
        } else {
            for (File file : childFileList) {
                result = file.delete();
                if (!result) {
                    break;
                }
            }
        }

        return result;
    }
}
