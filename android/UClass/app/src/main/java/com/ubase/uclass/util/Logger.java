package com.ubase.uclass.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.ConsoleMessage;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 로그 출력 Utility (파일 저장 및 공유 기능 포함)
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

    /** 로그 파일명 */
    private static final String LOG_FILE_NAME = "app_logs.txt";

    /** 날짜 포맷 */
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());

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

    /** Application Context */
    private static Context applicationContext;

    /** 파일 쓰기를 위한 ExecutorService */
    private static ExecutorService fileWriteExecutor = Executors.newSingleThreadExecutor();

    /**
     * Logger 초기화 (Application onCreate에서 호출)
     * @param context Application Context
     */
    public static void initialize(Context context) {
        applicationContext = context.getApplicationContext();
    }

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
     * 경고성 로그 출력
     * @param value 경고 메시지
     */
    public static void warning (String value) {
        printLog(LOG_LEVEL.WARN, TAG_INFO, getCallStack(), value);
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
        if (isPathPrint) {
            Throwable throwable = new Throwable();
            StackTraceElement[] stackElms = throwable.getStackTrace ();
            String [] callStack = new String[3];
            for(int i = 0; i < stackElms.length; i++) {
                if (!Logger.class.getName().equals(stackElms[i].getClassName())) {
                    callStack[0] = stackElms[i].getClassName();
                    callStack[1] = stackElms[i].getMethodName();
                    callStack[2] = String.valueOf(stackElms[i].getLineNumber());
                    break;
                }
            }
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
        } else {
            printLog = value;
        }

        String logMessage = (callStack == null ? "" : Arrays.toString(callStack) + " ") + printLog;

        /*
        V: 상세 (가장 낮은 우선순위)
        D: 디버그
        I: 정보
        W: 경고
        E: 오류
        A: 강제 종료
         */
        if (level == LOG_LEVEL.DEV) {
            Log.d(tag, logMessage);
        }
        else if (level == LOG_LEVEL.INFO) {
            Log.i(tag, logMessage);
        }
        else if (level == LOG_LEVEL.WARN) {
            Log.w(tag, logMessage);
        }
        else if (level == LOG_LEVEL.ERROR) {
            Log.e(tag, logMessage);
        }

        // 파일에 로그 저장
        writeToFile(level, logMessage);

        if (!TextUtils.isEmpty(moreLog)) {
            // 재귀함수
            printLog(level, tag, null, moreLog);
        }
    }

    // ========== 파일 관련 기능 ==========

    /**
     * 로그 파일 경로 가져오기
     * @return 로그 파일 객체
     */
    private static File getLogFile() {
        if (applicationContext == null) {
            Log.e(TAG_ERR, "Logger가 초기화되지 않았습니다. Logger.initialize(context)를 호출하세요.");
            return null;
        }
        File logDir = applicationContext.getFilesDir();
        return new File(logDir, LOG_FILE_NAME);
    }

    /**
     * 로그를 파일에 안전하게 저장
     * @param level 로그 레벨
     * @param message 로그 메시지
     */
    private static void writeToFile(final LOG_LEVEL level, final String message) {
        fileWriteExecutor.execute(new Runnable() {
            @Override
            public void run() {
                File logFile = getLogFile();
                if (logFile == null) return;

                try {
                    String timestamp = DATE_FORMAT.format(new Date());
                    String levelStr = "[" + level.name() + "]";
                    String logEntry = "[" + timestamp + "] " + levelStr + " " + message + "\n";

                    FileWriter writer = new FileWriter(logFile, true);
                    writer.append(logEntry);
                    writer.close();

                } catch (IOException e) {
                    Log.e(TAG_ERR, "로그 파일 쓰기 실패: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 로그 파일 내용 읽기
     * @return 로그 파일 내용 (실패 시 null)
     */
    public static String getLogContent() {
        File logFile = getLogFile();
        if (logFile == null || !logFile.exists()) {
            return null;
        }

        StringBuilder content = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(logFile));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            return content.toString();
        } catch (IOException e) {
            Log.e(TAG_ERR, "로그 파일 읽기 실패: " + e.getMessage());
            return null;
        }
    }

    /**
     * 로그 파일 안전하게 삭제
     */
    public static void clearLogs() {
        fileWriteExecutor.execute(new Runnable() {
            @Override
            public void run() {
                File logFile = getLogFile();
                if (logFile != null && logFile.exists()) {
                    if (logFile.delete()) {
                        Log.i(TAG_INFO, "로그 파일 삭제 완료");
                    } else {
                        Log.e(TAG_ERR, "로그 파일 삭제 실패");
                    }
                }
            }
        });
    }

    /**
     * 로그 파일 크기 확인 (바이트 단위)
     * @return 파일 크기
     */
    public static long getLogFileSize() {
        File logFile = getLogFile();
        if (logFile != null && logFile.exists()) {
            return logFile.length();
        }
        return 0;
    }

    /**
     * 로그 파일 정보 가져오기
     * @return 파일 존재 여부, 크기, 경로
     */
    public static LogFileInfo getLogFileInfo() {
        File logFile = getLogFile();
        if (logFile == null) {
            return new LogFileInfo(false, "0 bytes", null);
        }

        boolean exists = logFile.exists();
        long sizeInBytes = exists ? logFile.length() : 0;
        String sizeString = formatFileSize(sizeInBytes);
        String path = logFile.getAbsolutePath();

        return new LogFileInfo(exists, sizeString, path);
    }

    /**
     * 파일 크기 포맷팅
     * @param bytes 바이트 크기
     * @return 포맷된 문자열 (예: "1.5 MB")
     */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " bytes";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * 로그 파일이 너무 클 경우 오래된 로그 삭제 (선택사항)
     * @param maxSizeInMB 최대 파일 크기 (MB)
     */
    public static void rotateLogIfNeeded(final double maxSizeInMB) {
        fileWriteExecutor.execute(new Runnable() {
            @Override
            public void run() {
                long maxSizeInBytes = (long) (maxSizeInMB * 1024 * 1024);
                long currentSize = getLogFileSize();

                if (currentSize > maxSizeInBytes) {
                    info("로그 파일이 너무 큼 - 회전 처리");

                    String content = getLogContent();
                    if (content != null) {
                        String[] lines = content.split("\n");
                        int halfPoint = lines.length / 2;

                        StringBuilder newContent = new StringBuilder();
                        for (int i = halfPoint; i < lines.length; i++) {
                            newContent.append(lines[i]).append("\n");
                        }

                        File logFile = getLogFile();
                        if (logFile != null) {
                            try {
                                FileWriter writer = new FileWriter(logFile, false);
                                writer.write(newContent.toString());
                                writer.close();
                                info("로그 파일 회전 완료");
                            } catch (IOException e) {
                                error("로그 파일 회전 실패: " + e.getMessage());
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * 로그 파일을 외부 앱과 공유
     * @param context Activity Context
     */
    public static void shareLogFile(Context context) {
        File logFile = getLogFile();
        if (logFile == null || !logFile.exists()) {
            error("공유할 로그 파일이 없음");
            return;
        }

        if (!logFile.canRead()) {
            error("로그 파일을 읽을 수 없음");
            return;
        }

        try {
            // FileProvider를 통해 URI 생성
            String authority = context.getPackageName() + ".fileprovider";
            Uri fileUri = FileProvider.getUriForFile(context, authority, logFile);

            // 공유 Intent 생성
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "App Logs");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            // Chooser를 통해 앱 선택
            Intent chooser = Intent.createChooser(shareIntent, "로그 파일 공유");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);

            dev("로그 파일 공유 시작");

        } catch (Exception e) {
            error("로그 파일 공유 실패: " + e.getMessage());
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

    /**
     * 로그 파일 정보를 담는 클래스
     */
    public static class LogFileInfo {
        public final boolean exists;
        public final String size;
        public final String path;

        public LogFileInfo(boolean exists, String size, String path) {
            this.exists = exists;
            this.size = size;
            this.path = path;
        }
    }
}