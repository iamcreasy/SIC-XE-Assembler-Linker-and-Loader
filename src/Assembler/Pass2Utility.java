package Assembler;

import OperandPkg.Literal;
import OperandPkg.Operand;
import OperandPkg.OperandUtility;
import SymbolPkg.Node;
import SymbolPkg.SymbolTable;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.StringTokenizer;

/**
 *  Pass2 class of SICXE Assembler
 */
public class Pass2Utility {
    public static String HRecord = null;
    public static ArrayList<String> DRecordLists = new ArrayList<>();
    public static ArrayList<String> RRecordLists = new ArrayList<>();
    public static TRecordList TRecordLists = new TRecordList();
    public static ArrayList<String> MRecordLists = new ArrayList<>();
    public static String ERecord = null;

    public static boolean useBase = false;
    public static int baseAddress = 0;

    public static PrintWriter objectWriter, txtWriter;

    public static void generateObj(String inputFile, SymbolTable symbolTable, LinkedList<Literal> literalTable) throws IOException{
        String txtFile = inputFile.substring(0, inputFile.indexOf('.')).concat(".txt");
        String objFile = inputFile.substring(0, inputFile.indexOf('.')).concat(".o");

        txtWriter = new PrintWriter(txtFile, "UTF-8");
        objectWriter = new PrintWriter(objFile, "UTF-8");

        inputFile = inputFile.substring(0, inputFile.indexOf('.')).concat(".int");
        BufferedReader reader = new BufferedReader(new FileReader(inputFile));
        String instruction = reader.readLine();
        while (instruction != null){
            String objectCode = "";
            String[] fields = getFields(instruction);

            OperandUtility.evaluateOperand(symbolTable, literalTable, fields[3]);
            Operand operand = OperandUtility.operand;
            Literal literal = OperandUtility.literal;

// ASSEMBLER DIRECTIVE ********************************************************

            // START
            if(fields[2].equals("START")){
                String programName = Utility.pad(fields[1]);
                String startAddress = Utility.pad(Pass1Utility.startAddress, 6);
                String programLength = Utility.pad(Pass1Utility.programLength, 6);
                objectCode = "H^" + programName +"^" + startAddress +"^"+ programLength;

                txtWriter.println(instruction);
                HRecord = objectCode;
                instruction = reader.readLine();
                continue;
            }

            // BASE
            if(fields[2].equals("BASE")){
                baseAddress = operand.value;
                useBase = true;

                txtWriter.println(instruction);
                instruction = reader.readLine();
                continue;
            }

            // EQU, RESB and RESW
            if(fields[2].equals("EQU") | fields[2].equals("RESB") | fields[2].equals("RESW")){

                txtWriter.println(instruction);
                instruction = reader.readLine();
                continue;
            }

            // EXTDEF
            if(fields[2].equals("EXTDEF")){
                objectCode = objectCode.concat("D");

                StringTokenizer tokenizer = new StringTokenizer(fields[3], ",");
                while(tokenizer.hasMoreTokens()){
                    String symbolName = tokenizer.nextToken();
                    int symbolValue = symbolTable.search(symbolName).getValue();
                    objectCode = objectCode.concat("^").concat(Utility.pad(symbolName)).concat("^").concat(Utility.pad(symbolValue, 6));
                }

                txtWriter.println(instruction);
                DRecordLists.add(objectCode);
                instruction = reader.readLine();
                continue;
            }

            // EXTREF
            if(fields[2].equals("EXTREF")){
                objectCode = objectCode.concat("R");

                StringTokenizer tokenizer = new StringTokenizer(fields[3], ",");
                while (tokenizer.hasMoreTokens()){
                    String symbolName = tokenizer.nextToken();

                    Node externalSymbol = new Node(symbolName);
                    externalSymbol.value = 0;
                    externalSymbol.rflag = false;
                    externalSymbol.iflag = false;
                    externalSymbol.mflag = false; // redundant because default initializer
                    symbolTable.add(externalSymbol);

                    objectCode = objectCode.concat("^").concat(Utility.pad(symbolName));
                }

                txtWriter.println(instruction);
                RRecordLists.add(objectCode);
                instruction = reader.readLine();
                continue;
            }

            // BYTE C'abc' and BYTE X'0F'
            if(fields[2].equals("BYTE")){
                if(fields[3].contains("C'")){
                    // convert C'AB' to AB
                    String charValue = fields[3].substring(fields[3].indexOf("'")+1, fields[3].lastIndexOf("'"));

                    // Convert AB to 4142
                    String hexValue = "";
                    for(int i = 0; i<charValue.length(); i++) {
                        hexValue = hexValue.concat(Integer.toHexString((int) charValue.charAt(i)));
                    }

                    objectCode = hexValue.toUpperCase();
                    txtWriter.printf("%-60s%s\n",instruction, objectCode);
                    instruction = reader.readLine();
                    continue;
                }

                // BYTE X'0F'
                else if(fields[3].contains("X'")){
                    // convert X'1F' to 1F
                    String hexValue = fields[3].substring(fields[3].indexOf("'")+1, fields[3].lastIndexOf("'"));

                    objectCode = hexValue.toUpperCase();
                    txtWriter.printf("%-60s%s\n",instruction, objectCode);
                    instruction = reader.readLine();
                    continue;
                }

            }

            // WORD 97 or WORD ONE-TWO
            if(fields[2].equals("WORD")){
                // Object code of WORD 97 is 000061
                objectCode = Utility.pad(operand.value, 6);

                txtWriter.printf("%-60s%s\n",instruction, objectCode);
                MRecordLists.addAll(generateMRecord(fields, symbolTable));
                instruction = reader.readLine();
                continue;
            }

// OPCODE ********************************************************
            // lineCounter-field[0]   label-field[1]   opcode-field[2]     operand-field[3]

            // * C'ABC'
            if(fields[1] != null && fields[1].equals("*")){
                objectCode = findLiteralValue(literalTable, fields[2]);

                txtWriter.printf("%-60s%s\n",instruction, objectCode);
                instruction = reader.readLine();
                continue;
            }


            if(!fields[2].equals("END")){

                // 3A
                int opcode = Integer.parseInt(OpcodeUtility.getHexCode(fields[2]), 16);
                int addressingMode = getAddressingMode(operand);
                objectCode = objectCode.concat(Utility.pad(opcode + addressingMode, 2));

// format 1 ********************************************
                if(OpcodeUtility.getFormat(fields[2]) == 1) {
                    txtWriter.printf("%-60s%s\n",instruction, objectCode);
                    instruction = reader.readLine();
                    continue;
                }

// format 2 ********************************************
                if(OpcodeUtility.getFormat(fields[2]) == 2){

                    // get the value of the registers
                    StringTokenizer tokenizer = new StringTokenizer(fields[3], ",");
                    while(tokenizer.hasMoreTokens()){
                        String registerName = tokenizer.nextToken();
                        if(registerName != null)
                            objectCode = objectCode.concat(Integer.toString(Utility.getRegisterValue(registerName)));
                    }

                    // Pad the object code to length 4
                    while (objectCode.length()<4) {
                        objectCode = objectCode.concat("0");
                    }

                    txtWriter.printf("%-60s%s\n",instruction, objectCode);
                    instruction = reader.readLine();
                    continue;
                }

                // X bit
                int XBPE = 0;
                if(operand.Xbit){
                    XBPE += 8;
                }

// format 3 *********************************************
                if(OpcodeUtility.getFormat(fields[2]) == 3){

                    // format 3 with no operand
                    if(fields[3] == null){
                        objectCode = objectCode.concat("0000");

                        txtWriter.printf("%-60s%s\n",instruction, objectCode);
                        instruction = reader.readLine();
                        continue;
                    }

                    // TODO This section might have some problem
                    // format 3 with #1000 or 5 or 5+7
                    // LDA			#1			032001
                    // LDA          =C'ABC'     03200C
                    if(!operand.relocability & fields[3].charAt(0) != '='){   // if rflag = false == true AND there is no literal
                        objectCode = objectCode.concat(Utility.pad(XBPE, 1)).concat(Utility.pad(operand.value, 3));

                        txtWriter.printf("%-60s%s\n",instruction, objectCode);
                    }

                    // LDA ONE-2
                    // LDA #ARRAY (Where ARRAY is relative)
                    // for relocatable operand calculate XBPE and displacement
                    else {
                        int targetAddress, operandValue;

                        if(fields[3].charAt(0) == '='){
                            operandValue = findLiteralAddress(literalTable, fields[3]);
                        } else {
                            operandValue = operand.value;
                        }

                        targetAddress = operandValue - getNextLineCounter(fields);

                        // We need to move forward to get to the destination
                        if(operandValue >= Integer.parseInt(fields[0], 16)){

                            // check P range(positive)
                            if (targetAddress >= 0 && targetAddress <= 2047) {
                                XBPE += 2;
                                objectCode = objectCode.concat(Utility.pad(XBPE, 1)).concat(Utility.pad(targetAddress, 3));

                                txtWriter.printf("%-60s%s\n",instruction, objectCode);
//                                txtWriter.println(instruction + " " + objectCode + " $Positive address within range of P");
                                instruction = reader.readLine();
                                continue;
                            }

                            // check for B range
                            else {
                                // use Base register
                                if(useBase){
                                    // calculate target address using base register
                                    XBPE += 4;
                                    targetAddress = operandValue - baseAddress;

                                    // check for range
                                    if(targetAddress >= 0 && targetAddress <= 4095) { // 2^12 - 1 = 4096 - 1 = 4095
                                        objectCode = objectCode.concat(Utility.pad(XBPE, 1)).concat(Utility.pad(targetAddress, 3));
                                        txtWriter.printf("%-60s%s\n",instruction, objectCode);
//                                        txtWriter.println(instruction + " " + objectCode + " $Using Base Relative addressing");  // printing objectcode
                                    }

                                    // out of range for Base register
                                    else {
                                        objectCode = "Error : Positive address out of range of B";
                                        txtWriter.printf("%-60s%s\n",instruction, objectCode);
//                                        txtWriter.println(instruction + " $Error : Positive address out of range of B : " + Utility.pad(targetAddress, 5)); // printing objectcode
                                    }
                                }

                                // Can not use Base regsiter becasue it's not in use
                                else {
                                    objectCode = "Error : Address out of range, Try Base addressing.";
                                    txtWriter.printf("%-60s%s\n",instruction, objectCode);
//                                    txtWriter.println(instruction + " $Error : Positive address out of range of P, try Base addressing. " + Utility.pad(targetAddress, 5)); // printing objectcode
                                }
                            }
                        }

                        // When we need to move backward to get to the destination
                        else if(operandValue < Integer.parseInt(fields[0], 16)){

                            // Check P range (Negative)
                            if(targetAddress <= -1 && targetAddress >= -2048){
                                XBPE += 2;
                                objectCode = objectCode.concat(Utility.pad(XBPE, 1)).concat(Utility.pad(targetAddress, 3));

                                txtWriter.printf("%-60s%s\n",instruction, objectCode);
//                                txtWriter.println(instruction + " " + objectCode + " $Negative displacement within range of P");    // printing objectcode
                            }

                            // Out or negative P range
                            else {
                                objectCode = "Error : Negative displacement out of range";
                                txtWriter.printf("%-60s%s\n",instruction, objectCode);
//                                txtWriter.println(instruction + " $Error : Negative displacement out of range of P :" + Utility.pad(targetAddress, 5));   // printing objectcode
                            }
                        }

                    }
                }

// format 4 ***********************************
                else if(OpcodeUtility.getFormat(fields[2]) == 4) {
                    XBPE += 1;
                    objectCode = objectCode.concat(Utility.pad(XBPE, 1)).concat(Utility.pad(operand.value, 5));

                    txtWriter.printf("%-60s%s\n",instruction, objectCode);
//                    TRecordList.add(objectCode, 4);
                    MRecordLists.addAll(generateMRecord(fields, symbolTable));
                }

                // after processing format 3 and format 4 instruction, go to next line
                instruction = reader.readLine();
                continue;
            }

// END directive has been reached   *****************
            else {
                // END directive with operand
                if(fields[3] != null) {
                    objectCode = objectCode.concat("E^").concat(Utility.pad(operand.value, 6));
                } else {
                    objectCode = objectCode.concat("E^");
                }

                txtWriter.println(instruction);
                ERecord = objectCode;
                instruction = reader.readLine();
                continue;
            }
        }

        txtWriter.close();

        // Write the Object File
        objectWriter.println(HRecord);
        for(String d : DRecordLists)
            objectWriter.println(d);
        for(String r : RRecordLists)
            objectWriter.println(r);
        // print the T records
        for(String m : MRecordLists)
            objectWriter.println(m);
        objectWriter.println(ERecord);
        objectWriter.close();

    }

    /**
     * Generate M records given the Symbol Table, and the full instruction as an array of string.
     * @param fields Given instruction split into array of strings
     * @param symbolTable symbol table to check for the rflag of the symbol found in the operand
     * @return Returns the list of generated M records.
     */
    private static ArrayList<String> generateMRecord(String[] fields, SymbolTable symbolTable) {
        ArrayList<String> MRecordList = new ArrayList<>();

        int offset = 0;
        String nibbles;
        if(fields[2].equals("WORD") | fields[2].equals("BYTE")){
            nibbles = "06";
            offset = 0;
        }
        else {
            nibbles = "05";
            offset = 1;
        }

        // always M record for external symbol
        for (Node symbol : symbolTable.getAllExternal()) {
            int index = fields[3].indexOf(symbol.getKey()); // find if the symbol exists in the operand

            if (index != -1) {
                char ch = getSignOfSymbol(fields[3], index); // check for sign
                String genMRec = "M^" + Utility.pad(Integer.parseInt(fields[0], 16) + offset, 6) + "^" + nibbles + "^" + ch + Utility.pad(symbol.getKey());
                MRecordList.add(genMRec);
            }
        }

        // if the operand is relocatable, M record for all relocatable symbols
        if(OperandUtility.operand.relocability) {
            for (Node symbol : symbolTable.getAll()) {
                int index = fields[3].indexOf(symbol.getKey()); // find if the symbol exists in the operand
                String controlSection = (symbol.getIflag() ? Pass1Utility.controlSectionName : symbol.getKey()); // identify control section

                if (index != -1 && symbol.rflag) {
                    char ch = getSignOfSymbol(fields[3], index); // check for sign
                    String genMRec = "M^" + Utility.pad(Integer.parseInt(fields[0], 16) + offset, 6) + "^" + nibbles + "^" + ch + Utility.pad(controlSection);
                    MRecordList.add(genMRec);
                    break;
                }
            }
        }

        return MRecordList;
    }

    /**
     * Returns array of strings containing fields of an intermediate instruction.
     * If any field doesn't exists, it's set to null.
     * @param instruction the intermediate instruction to be processed
     * @return an array of string of length 4
     */
    private static String[] getFields(String instruction) {
        // 0 lineCounter     15 label      30 opcode         45 operand

        String[] fields = new String[4]; // all array elements are initialized to 'null'

        StringTokenizer tokenizer = new StringTokenizer(instruction);

        // get line count
        fields[0] = tokenizer.nextToken();

        // get full length label if exists
        if(!(instruction.charAt(15) <= 32)) {
            fields[1] = tokenizer.nextToken();
        }

        // get opcode
        fields[2] = tokenizer.nextToken();

        // get operand if exists
        if(tokenizer.hasMoreTokens())
            fields[3] = tokenizer.nextToken();

        return fields;
    }

    /**
     *
     * @param operand
     * @return
     */
    public static int getAddressingMode(Operand operand){
        if(!operand.Nbit && operand.Ibit)
            return 1;
        else if(operand.Nbit && !operand.Ibit)
            return 2;
        else
            return 3;
    }

    /**
     *
     * @param literalTable
     * @param literalExpression
     * @return
     */
    private static int findLiteralAddress(LinkedList<Literal> literalTable, String literalExpression){
        // remove the '=' character since none of the literal on literal table has that character
        literalExpression = literalExpression.substring(1);

        for(Literal literal : literalTable){
            if(literal.name.equals(literalExpression))
                return literal.address;
        }

        return -1;
    }

    private static String findLiteralValue(LinkedList<Literal> literalTable, String literalExpression){
        for(Literal literal : literalTable){
            if(literal.name.equals(literalExpression))
                return literal.value;
        }

        return null;
    }

    /**
     *
     * @param fields
     * @return
     */
    private static int getNextLineCounter(String[] fields){
        // current list counter
        int currentLineCounter = Integer.parseInt(fields[0], 16);

        // handle format 1/2/3/4 opcode
        int format = OpcodeUtility.getFormat(fields[2]);
        if(format != 0){
            return currentLineCounter + format;
        }

        // handle BYTE, WORD, RESB, RESW
        else {
            if(fields[2].equals("BYTE")){
                String temp = fields[3].substring(fields[3].indexOf('\'')+1, fields[3].lastIndexOf('\''));
                if(fields[2].contains("C"))
                    return currentLineCounter + temp.length();
                else
                    return currentLineCounter + temp.length() / 2;
            }

            else if(fields[2].equals("WORD")){
                return currentLineCounter + 3;
            }

            else if(fields[2].equals("RESW")){
                return currentLineCounter + 3 * Integer.parseInt(fields[3]);
            }

            else if(fields[2].equals("RESB")){
                return currentLineCounter + Integer.parseInt(fields[3]);
            }
        }

        return currentLineCounter;
    }

    /**
     *
     * @param operand
     * @param indexOfSymbol
     * @return
     */
    private static char getSignOfSymbol(String operand, int indexOfSymbol){
        try {
            if (operand.charAt(indexOfSymbol - 1) == '-') {
                return '-';
            }
        } catch(StringIndexOutOfBoundsException e) {
            return '+';
        }

        return '+';
    }


}
