/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lpcmanager;

/**
 *
 * @author user
 */
public class Command {

    public final String commandString;
    private String response;
    private COMMAND_STATUS status;

    private static enum COMMAND_STATUS {
        PENDING, OK, FAILED, TIMEOUT
    }

    private Command(String commandString) {
        this.commandString = commandString;
        this.status = Command.COMMAND_STATUS.PENDING;
        this.response = "PENDING";
    }

    public String getResponse(){
        return this.response;
    }
    
    public void setResponse(String response) {
        this.response = response;
    }
    
    public static Command errorTest() {
        return new Command("error, \r\n");
    }

    public static Command test() {
        return new Command("test,1,2,3 \r\n");
    }
    
    public static Command test1() {
        return new Command("test,3,2,1 \r\n");
    }
    
    
    public static Command getSysUptime() {
        return new Command("getSysUptime\r\n");
    }
    
    public static Command screenOff() {
        return new Command("screenOff\r\n");
    }
//    public static Command screenOff() {
//        return new Command("screenOff\r\n");
//    }
//    public static Command screenOff() {
//        return new Command("screenOff\r\n");
//    }
//    public static Command screenOff() {
//        return new Command("screenOff\r\n");
//    }
//    public static Command screenOff() {
//        return new Command("screenOff\r\n");
//    }
//    public static Command screenOff() {
//        return new Command("screenOff\r\n");
//    }
//    public static Command screenOff() {
//        return new Command("screenOff\r\n");
//    }
//    public static Command screenOff() {
//        return new Command("screenOff\r\n");
//    }
//    public static Command screenOff() {
//        return new Command("screenOff\r\n");
//    }

    public static Command screenOn() {
        return new Command("screenOn\r\n");
    }

    public static Command screenClear() {
        return new Command("screenClear\r\n");
    }
    /*

    public ScreenStatus screenStatus() {
        String response = null;

        try {
            logger.info("screenStatus\r\n");
            out.write("screenStatus\r\n");
            out.flush();
            response = in.readLine();
        } catch (IOException e) {
            throw new LPCException("LPC I/O error!", e);
        }

        if (!response.startsWith("ok")) {
            throw new LPCException("error response : " + response);
        }

        String[] results = StringUtils.split(response, ",");

        ScreenStatus screenStatus = ScreenStatus.fromId(results[1]);

        return screenStatus;
    }

    public Dimension screenSize() {
        String response = null;

        try {
            logger.info("screenSize\r\n");
            out.write("screenSize\r\n");
            out.flush();
            response = in.readLine();
        } catch (IOException e) {
            throw new LPCException("LPC I/O error!", e);
        }

        if (!response.startsWith("ok")) {
            throw new LPCException("error response : " + response);
        }

        String[] results = StringUtils.split(response, ",");

        Dimension dimension = new Dimension();
        dimension.setSize(Integer.valueOf(results[1]), Integer.valueOf(results[2]));

        return dimension;
    }

    public void screenFont(ScreenFont screenFont, Integer spacing) {
        String response = null;

        try {
            StringJoiner joiner = new StringJoiner(",");
            joiner.add("screenFont");
            joiner.add(screenFont.getId());
            if (spacing != null) {
                joiner.add(String.valueOf(spacing));
            }
            joiner.add("\r\n");

            logger.info(joiner.toString());
            out.write(joiner.toString());
            out.flush();
            response = in.readLine();
        } catch (IOException e) {
            throw new LPCException("LPC I/O error!", e);
        }

        if (!response.startsWith("ok")) {
            throw new LPCException("error response : " + response);
        }
    }

    public void screenText(Integer x, Integer y, String text, Integer width, Integer height) {
        String response = null;

        try {
            StringJoiner joiner = new StringJoiner(",");
            joiner.add("screenText");
            joiner.add(String.valueOf(x));
            joiner.add(String.valueOf(y));
            joiner.add(text);
            if (width != null && height != null) {
                joiner.add(String.valueOf(width));
                joiner.add(String.valueOf(height));
            }
            joiner.add("\r\n");
            logger.info(joiner.toString());
            out.write(joiner.toString());
            out.flush();
            response = in.readLine();
        } catch (IOException e) {
            throw new LPCException("LPC I/O error!", e);
        }

        if (!response.startsWith("ok")) {
            throw new LPCException("error response : " + response);
        }

        ids.add(response.substring(3));
    }

    public void screenDrawBitmapFile(int x, int y, String filename) {
        String response = null;

        try {
            StringJoiner joiner = new StringJoiner(",");
            joiner.add("screenDrawBitmapFile");
            joiner.add(String.valueOf(x));
            joiner.add(String.valueOf(y));
            joiner.add(filename);
            joiner.add("\r\n");
            logger.info(joiner.toString());
            out.write(joiner.toString());
            out.flush();
            response = in.readLine();
        } catch (IOException e) {
            throw new LPCException("LPC I/O error!", e);
        }

        if (!response.startsWith("ok")) {
            throw new LPCException("error response : " + response);
        }

        ids.add(response.substring(3));
    }

    public void screenSetBacklight(String color) {
        String response = null;

        try {
            logger.info("screenSetBacklight," + color + "\r\n");
            out.write("screenSetBacklight," + color + "\r\n");
            out.flush();
            response = in.readLine();
        } catch (IOException e) {
            throw new LPCException("LPC I/O error!", e);
        }

        if (!response.startsWith("ok")) {
            throw new LPCException("error response : " + response);
        }
    }

    public void screenStoreBitmapFile(int index, String filename) {
        String response = null;

        try {
            logger.info("screenStoreBitmapFile," + index + ",128,64," + filename + "\r\n");
            out.write("screenStoreBitmapFile," + index + ",128,64," + filename + "\r\n");
            out.flush();
            response = in.readLine();
        } catch (IOException e) {
            throw new LPCException("LPC I/O error!", e);
        }

        if (!response.startsWith(">ok")) {
            throw new LPCException("error response : " + response);
        }
    }

    public void screenStoreBitmapFile(int index, int x, int y, int size, byte[] bytes) {
        String response = null;

        try {
            logger.info("screenStoreBitmapBuffer," + index + "," + x + "," + y + "," + size + "," + "\r\n");
            out.write("screenStoreBitmapBuffer," + index + "," + x + "," + y + "," + size + "," + "\r\n");
            out.flush();
            int repeats = 0;
            while (!in.ready()) {
                repeats++;
                if (repeats > 10) {
                    break;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                }

            }
            char c = (char) in.read();
            if (c != '>') {
                throw new IOException("Invalid command output(not the > char): " + c);
            }

            logger.info("About to write " + bytes.length + " to the LPCManager");

            outputStream.write(bytes);
            outputStream.flush();
            response = in.readLine();
        } catch (IOException e) {
            throw new LPCException("LPC I/O error!", e);
        }

        if (!response.startsWith("ok")) {
            throw new LPCException("error response : " + response);
        }
    }

    public void screenDrawBitmap(int index, int positionx, int positiony, int width, int height) {
        String response = null;

        try {
            logger.info("screenDrawBitmap," + index + "," + positionx + "," + positiony + "," + width + "," + height + "\r\n");
            out.write("screenDrawBitmap," + index + "," + positionx + "," + positiony + "," + width + "," + height + "\r\n");
            out.flush();
            response = in.readLine();
        } catch (IOException e) {
            throw new LPCException("LPC I/O error!", e);
        }

        if (!response.startsWith("ok")) {
            throw new LPCException("error response : " + response);
        }

        ids.add(response.substring(3));
    }

    public void screenRemoveObjects() {
        String response = null;
        while (!ids.isEmpty()) {
            try {
                //Remove from last to first for better results.
                //Perhaps we need this list to be a stack.
                String id = ids.pollLast();
                logger.info("screenRemoveObject," + id + "\r\n");
                out.write("screenRemoveObject," + id + "\r\n");
                out.flush();
                response = in.readLine();
            } catch (IOException e) {
                logger.error("LPC I/O error while removing objects!", e);
                throw new LPCException("LPC I/O error!", e);
            }
        }
    }

    public void setGreenLEDPattern() {
        String response = null;

        try {
            logger.info("setLEDPattern,1,150,65280,150,0" + "\r\n");
            out.write("setLEDPattern,1,150,65280,150,0" + "\r\n");
            out.flush();
            response = in.readLine();
        } catch (IOException e) {
            throw new LPCException("LPC I/O error!", e);
        }

        if (!response.startsWith("ok")) {
            throw new LPCException("error response : " + response);
        }
    }

    public void setRedLEDPattern() {
        String response = null;

        try {
            logger.info("setLEDPattern,1,150,255,150,0" + "\r\n");
            out.write("setLEDPattern,1,150,255,150,0" + "\r\n");
            out.flush();
            response = in.readLine();
        } catch (IOException e) {
            throw new LPCException("LPC I/O error!", e);
        }

        if (!response.startsWith("ok")) {
            throw new LPCException("error response : " + response);
        }
    }

    public void setBuzzerValid() {
        String response = null;

        try {
            logger.info("setBuzzerPattern,200,0,0" + "\r\n");
            out.write("setBuzzerPattern,200,0,0" + "\r\n");
            out.flush();
            response = in.readLine();
        } catch (IOException e) {
            throw new LPCException("LPC I/O error!", e);
        }

        if (!response.startsWith("ok")) {
            throw new LPCException("error response : " + response);
        }
    }

    public void setBuzzerInvalid() {
        String response = null;

        try {
            logger.info("setBuzzerPattern,150,150,1" + "\r\n");
            out.write("setBuzzerPattern,150,150,1" + "\r\n");
            out.flush();
            response = in.readLine();
        } catch (IOException e) {
            throw new LPCException("LPC I/O error!", e);
        }

        if (!response.startsWith("ok")) {
            throw new LPCException("error response : " + response);
        }
    }

    public enum ScreenStatus {
        SCREEN_OFF("0"), SCREEN_ON("1"), SCREEN_IDLE("2");

        private String id;

        ScreenStatus(String id) {
            this.id = id;
        }

        public static ScreenStatus fromId(String id) {
            for (ScreenStatus s : values()) {
                if (s.id.equals(id)) {
                    return s;
                }
            }
            return null;
        }
    }

    public enum ScreenFont {
        DEFAULT("0"),
        AMCO_5X7_LATIN_GREEK_VARIABLE_WIDTH("1"),
        ARIAL_10X14_LATIN_VARIABLE_WIDTH("2"),
        ARIAL_BOLD_10X14_LATIN_VARIABLE_WIDTH("3"),
        CALIBRI_BLACK_28X36_LATIN_VARIABLE_WIDTH("4"),
        CALIBRI_10X10_LATIN_VARIABLE_WIDTH("5"),
        CALIBRI_BOLD_10X11_LATIN_VARIABLE_WIDTH("6"),
        CALIBRI_10X11_LATIN_VARIABLE_WIDTH("7"),
        CALIBRI_ITALIC_10X11_LATIN_VARIABLE_WIDTH("8"),
        CALIBRI_10X14_LATIN_VARIABLE_WIDTH("9"),
        CALIBRI_BOLD_10X15_LATIN_VARIABLE_WIDTH("10"),
        CALIBRI_10X15_LATIN_VARIABLE_WIDTH("11"),
        CALIBRI_BOLD_10X23_LATIN_VARIABLE_WIDTH("12"),
        CALIBRI_BOLD_28X33_LATIN_VARIABLE_WIDTH("13"),
        CALIBRI_LITE_10X25_LATIN_VARIABLE_WIDTH("14"),
        COOPER_10X19_LATIN_VARIABLE_WIDTH("15"),
        COOPER_10X21_LATIN_VARIABLE_WIDTH("16"),
        COOPER_10X26_LATIN_VARIABLE_WIDTH("17"),
        CORSIVA_10X11_LATIN_VARIABLE_WIDTH("18"),
        CP437_8X8_LATIN_VARIABLE_WIDTH("19"),
        FIXED_BOLD_10X15_LATIN_FIXED_WIDTH("20"),
        FIXED_15X31_DIGITS_FIXED_WIDTH("21"),
        FIXED_7X15_DIGITS_FIXED_WIDTH("22"),
        FIXED_8X15_DIGITS_FIXED_WIDTH("23"),
        FONT_8X8_LATIN_VARIABLE_WIDTH("24"),
        IAIN_5X7_LATIN_VARIABLE_WIDTH("25"),
        LCD_11X15_DIGITS_VARIABLE_WIDTH("26"),
        LCD_13X23_DIGITS_VARIABLE_WIDTH("27"),
        NEW_BASIC_3X5_LATIN_VARIABLE_WIDTH("28"),
        ROOSEWOOD_10X22_LATIN_VARIABLE_WIDTH("29"),
        ROOSEWOOD_10X26_LATIN_VARIABLE_WIDTH("30"),
        SYSTEM_5X7_LATIN_VARIABLE_WIDTH("31"),
        TIMES_NEW_ROMAN_10X13_LATIN_VARIABLE_WIDTH("32"),
        TIMES_NEW_ROMAN_ITALIC_10X13_LATIN_VARIABLE_WIDTH("33"),
        TIMES_NEW_ROMAN_BOLD_10X16_LATIN_VARIABLE_WIDTH("34");

        private String id;

        ScreenFont(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public static ScreenFont fromId(String id) {
            for (ScreenFont s : values()) {
                if (s.id.equals(id)) {
                    return s;
                }
            }
            return null;
        }
    }
*/
}
