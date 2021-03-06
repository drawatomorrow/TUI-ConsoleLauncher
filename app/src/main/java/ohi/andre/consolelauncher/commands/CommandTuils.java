package ohi.andre.consolelauncher.commands;

import android.annotation.SuppressLint;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ohi.andre.consolelauncher.managers.AppsManager;
import ohi.andre.consolelauncher.managers.ContactManager;
import ohi.andre.consolelauncher.managers.FileManager;
import ohi.andre.consolelauncher.managers.FileManager.DirInfo;
import ohi.andre.consolelauncher.managers.MusicManager;
import ohi.andre.consolelauncher.tuils.ShellUtils;
import ohi.andre.consolelauncher.tuils.SpecificExtensionFileFilter;
import ohi.andre.consolelauncher.tuils.SpecificNameFileFilter;
import ohi.andre.consolelauncher.tuils.Tuils;

@SuppressLint("DefaultLocale")
public class CommandTuils {

    private static final int MIN_CONTACT_RATE = 4;
    private static final int MIN_SONG_RATE = 5;

    private static SpecificExtensionFileFilter extensionFileFilter = new SpecificExtensionFileFilter();
    private static SpecificNameFileFilter nameFileFilter = new SpecificNameFileFilter();

    //	parse a command
    public static Command parse(String input, ExecInfo info, boolean suggestion) throws Exception {
        Command command = new Command();

        boolean pendingSuVerification = false;
        if (!suggestion && isSuCommand(input)) {
            input = input.substring(3);
            pendingSuVerification = true;
        }

        String name = CommandTuils.findName(input);
        if (!Tuils.isAlpha(name))
            return null;

        CommandAbstraction cmd = info.commandGroup.getCommandByName(name);
        if (cmd == null)
            return null;

        if (pendingSuVerification)
            info.setSu(Tuils.verifyRoot());

        command.cmd = cmd;

        input = input.substring(name.length());

        int[] types = cmd.argType();
        ArrayList<Object> args = new ArrayList<>(cmd.maxArgs() == CommandAbstraction.UNDEFINIED ? 0 : cmd.maxArgs());
        int nArgs = 0;

        if (types != null) {
            for (int type : types) {
                input = input.trim();
                if (input.length() <= 0)
                    break;

                ArgInfo arg = CommandTuils.getArg(info, input, type, suggestion);

                if (!arg.found) {
                    command.nArgs = Command.ARG_NOTFOUND;
                    return command;
                }

                nArgs += arg.n;
                args.add(arg.arg);
                input = arg.residualString;
            }
        }

        command.mArgs = args.toArray(new Object[args.size()]);
        command.nArgs = nArgs;

        return command;
    }

    //	find command name
    private static String findName(String input) {
        int space = input.indexOf(Tuils.SPACE);

        input = input.toLowerCase();

        if (space == -1)
            return input;
        else
            return input.substring(0, space);
    }

    //	find args
    public static ArgInfo getArg(ExecInfo info, String input, int type, boolean suggestion) {
        if (type == CommandAbstraction.FILE)
            return file(input, info.currentDirectory);
        else if (type == CommandAbstraction.CONTACTNUMBER)
            return contactNumber(input, info.contacts);
//        will always find a plain text
        else if (type == CommandAbstraction.PLAIN_TEXT)
            return plainText(input);
        else if (type == CommandAbstraction.PACKAGE)
            return packageName(input, info.appsManager);
//        will always find a textlist
        else if (type == CommandAbstraction.TEXTLIST)
            return textList(input);
        else if (type == CommandAbstraction.SONG)
            return song(input, info.player);
        else if (type == CommandAbstraction.FILE_LIST)
            if(suggestion)
                return file(input, info.currentDirectory);
            else
                return fileList(input, info.currentDirectory);
        else if (type == CommandAbstraction.COMMAND)
            return command(input, info.commandGroup);
        else if (type == CommandAbstraction.PARAM)
            return param(input);

        return null;
    }


//	args extractors {

    private static ArgInfo plainText(String input) {
        return new ArgInfo(input, "", true, 1);
    }

    private static ArgInfo textList(String input) {
        if (input == null) {
            return null;
        }

        List<String> strings = new ArrayList<>();

        char[] chars = input.toCharArray();
        String arg = "";
        int index;

        for (index = 0; index < chars.length; index++) {
            char c = chars[index];
            if (c == ' ') {
                if (arg.length() > 0) {
                    strings.add(arg);
                    arg = "";
                    continue;
                } else {
//                	prevent double space
                    continue;
                }
            }

            arg = arg.concat(c + Tuils.EMPTYSTRING);
        }

        if (arg.length() > 0)
            strings.add(arg);

        return new ArgInfo(strings, input.substring(index), true, strings.size());
    }

    private static ArgInfo command(String string, CommandGroup active) {
        CommandAbstraction abstraction = null;
        try {
            abstraction = active.getCommandByName(string);
        } catch (Exception e) {}

        return new ArgInfo(abstraction, null, abstraction != null, 1);
    }

    @SuppressWarnings("unchecked")
    private static ArgInfo file(String input, File cd) {
        List<String> strings = (List<String>) CommandTuils.textList(input).arg;

        String toVerify = "";
        for (int count = 0; count < strings.size(); count++) {
            toVerify = toVerify.concat(strings.get(count));

            DirInfo info = CommandTuils.getFile(toVerify, cd);
            if (info.file != null && info.notFound == null) {
                while (count-- >= 0)
                    strings.remove(0);

                String residual = Tuils.toPlanString(strings, " ");
                return new ArgInfo(info.file, residual, true, 1);
            }

            toVerify = toVerify.concat(" ");
        }

        return new ArgInfo(null, input, false, 0);
    }

    @SuppressWarnings("unchecked")
    private static ArgInfo fileList(String input, File cd) {
        List<File> files = new ArrayList<>();
        List<String> strings = (List<String>) CommandTuils.textList(input).arg;

        String toVerify = "";
        for (int count = 0; count < strings.size(); count++) {
            String s = strings.get(count);

            toVerify = toVerify.concat(s);

            DirInfo dir = CommandTuils.getFile(toVerify, cd);
            if (dir.notFound == null) {
                files.add(dir.file);

                toVerify = "";
                continue;
            }

            List<File> tempFiles = CommandTuils.attemptWildcard(dir);
            if (tempFiles != null) {
                files.addAll(tempFiles);

                toVerify = "";
                continue;
            }

            toVerify = toVerify.concat(" ");
        }

        if (toVerify.length() > 0)
            return new ArgInfo(null, null, false, 0);

        return new ArgInfo(files, null, files.size() > 0, files.size());
    }

    private static DirInfo getFile(String path, File cd) {
        return FileManager.cd(cd, path);
    }

    private static List<File> attemptWildcard(DirInfo dir) {
        List<File> files;

        FileManager.WildcardInfo info = FileManager.wildcard(dir.notFound);
        if(info == null) {
            return null;
        }

        File cd = dir.file;
        if (!cd.isDirectory()) {
            return null;
        }

        if (info.allExtensions && info.allNames) {
            files = Arrays.asList(cd.listFiles());
        } else if(info.allNames) {
            extensionFileFilter.setExtension(info.extension);
            files = Arrays.asList(cd.listFiles(extensionFileFilter));
        } else if(info.allExtensions) {
            nameFileFilter.setName(info.name);
            files = Arrays.asList(cd.listFiles(nameFileFilter));
        } else {
            return null;
        }

        if (files.size() > 0) {
            return files;
        } else {
            return null;
        }
    }

    private static ArgInfo param(String input) {
        String param = null;
        int indexOfFirstSpace = 0;

        if (input.startsWith("-")) {
            indexOfFirstSpace = input.indexOf(Tuils.SPACE);
            if (indexOfFirstSpace == -1)
                indexOfFirstSpace = input.length();

            param = input.substring(0, indexOfFirstSpace);
        }

        return new ArgInfo(param, param != null ? input.substring(indexOfFirstSpace) : input, param != null, param != null ? 1 : 0);
    }

    private static ArgInfo packageName(String input, AppsManager apps) {
        String packageName = apps.findPackage(input);
        return new ArgInfo(packageName, null, packageName != null, 1);
    }

    private static ArgInfo contactNumber(String input, ContactManager contacts) {
        String number;

        if (Tuils.isNumber(input))
            number = input;
        else
            number = contacts.findNumber(input, MIN_CONTACT_RATE);

        return new ArgInfo(number, null, number != null, 1);
    }

    private static ArgInfo song(String input, MusicManager music) {
        String name = music.getSong(input, MIN_SONG_RATE);
        return new ArgInfo(name, null, name != null, 1);
    }

    public static boolean isSuRequest(String input) {
        return input.equals(ShellUtils.COMMAND_SU);
    }

    public static boolean isSuCommand(String input) {
        return input.startsWith(ShellUtils.COMMAND_SU + Tuils.SPACE);
    }

    public static class ArgInfo {
        public Object arg;
        public String residualString;
        public int n;
        public boolean found;

        public ArgInfo(Object a, String s, boolean f, int i) {
            this.arg = a;
            this.residualString = s;
            this.found = f;
            this.n = i;
        }
    }

}
