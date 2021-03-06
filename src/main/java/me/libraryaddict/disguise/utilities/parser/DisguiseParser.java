package me.libraryaddict.disguise.utilities.parser;

import me.libraryaddict.disguise.DisguiseConfig;
import me.libraryaddict.disguise.disguisetypes.*;
import me.libraryaddict.disguise.utilities.DisguiseUtilities;
import me.libraryaddict.disguise.utilities.parser.params.ParamInfo;
import me.libraryaddict.disguise.utilities.translations.LibsMsg;
import me.libraryaddict.disguise.utilities.translations.TranslateType;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;

public class DisguiseParser {
    private static void doCheck(CommandSender sender, DisguisePermissions permissions, DisguisePerm disguisePerm,
            Collection<String> usedOptions) throws DisguiseParseException {

        if (!permissions.isAllowedDisguise(disguisePerm, usedOptions)) {
            throw new DisguiseParseException(LibsMsg.D_PARSE_NOPERM,
                    usedOptions.stream().reduce((first, second) -> second).orElse(null));
        }
    }

    private static HashMap<String, Boolean> getDisguiseOptions(CommandSender sender, String permNode,
            DisguisePerm type) {
        switch (type.getType()) {
            case PLAYER:
            case FALLING_BLOCK:
            case PAINTING:
            case SPLASH_POTION:
            case FISHING_HOOK:
            case DROPPED_ITEM:
                HashMap<String, Boolean> returns = new HashMap<>();

                String beginning = "libsdisguises.options." + permNode + ".";

                for (PermissionAttachmentInfo permission : sender.getEffectivePermissions()) {
                    String lowerPerm = permission.getPermission().toLowerCase();

                    if (lowerPerm.startsWith(beginning)) {
                        String[] split = lowerPerm.substring(beginning.length()).split("\\.");

                        if (split.length > 1) {
                            if (split[0].replace("_", "").equals(type.toReadable().toLowerCase().replace(" ", ""))) {
                                for (int i = 1; i < split.length; i++) {
                                    returns.put(split[i], permission.getValue());
                                }
                            }
                        }
                    }
                }

                return returns;
            default:
                return new HashMap<>();
        }
    }

    public static DisguisePerm getDisguisePerm(String name) {
        for (DisguisePerm perm : getDisguisePerms()) {
            if (!perm.toReadable().replaceAll("[ |_]", "").equalsIgnoreCase(name.replaceAll("[ |_]", "")))
                continue;

            return perm;
        }

        if (name.equalsIgnoreCase("p"))
            return getDisguisePerm(DisguiseType.PLAYER.toReadable());

        return null;
    }

    public static DisguisePerm[] getDisguisePerms() {
        DisguisePerm[] perms = new DisguisePerm[DisguiseType.values().length +
                DisguiseConfig.getCustomDisguises().size()];
        int i = 0;

        for (DisguiseType disguiseType : DisguiseType.values()) {
            perms[i++] = new DisguisePerm(disguiseType);
        }

        for (Entry<String, Disguise> entry : DisguiseConfig.getCustomDisguises().entrySet()) {
            perms[i++] = new DisguisePerm(entry.getValue().getType(), entry.getKey());
        }

        return perms;
    }

    /**
     * Get perms for the node. Returns a hashmap of allowed disguisetypes and their options
     */
    public static DisguisePermissions getPermissions(CommandSender sender, String commandName) {
        return new DisguisePermissions(sender, commandName);
    }

    private static boolean isDouble(String string) {
        try {
            Float.parseFloat(string);
            return true;
        }
        catch (Exception ex) {
            return false;
        }
    }

    private static boolean isInteger(String string) {
        try {
            Integer.parseInt(string);
            return true;
        }
        catch (Exception ex) {
            return false;
        }
    }

    /**
     * Returns true if the string is found in the map, or it's not a whitelisted setup
     *
     * Returns if command user can access the disguise creation permission type
     */
    private static boolean hasPermissionOption(HashMap<String, Boolean> disguiseOptions, String string) {
        // If no permissions were defined, return true
        if (disguiseOptions.isEmpty()) {
            return true;
        }

        // If they were explictly defined, can just return the value
        if (disguiseOptions.containsKey(string)) {
            return disguiseOptions.get(string);
        }

        // If there is at least one whitelisted value, then they needed the whitelist to use it
        return disguiseOptions.containsValue(true);
    }

    /**
     * Returns the disguise if it all parsed correctly. Returns a exception with a complete message if it didn't. The
     * commandsender is purely used for checking permissions. Would defeat the purpose otherwise. To reach this
     * point, the
     * disguise has been feed a proper disguisetype.
     */
    public static Disguise parseDisguise(CommandSender sender, String permNode, String[] args,
            DisguisePermissions permissions) throws DisguiseParseException, IllegalAccessException,
            InvocationTargetException {
        if (sender instanceof Player) {
            DisguiseUtilities.setCommandsUsed();
        }

        if (!permissions.hasPermissions()) {
            throw new DisguiseParseException(LibsMsg.NO_PERM);
        }

        if (args.length == 0) {
            throw new DisguiseParseException(LibsMsg.PARSE_NO_ARGS);
        }

        // How many args to skip due to the disugise being constructed
        // Time to start constructing the disguise.
        // We will need to check between all 3 kinds of disguises
        int toSkip = 1;
        ArrayList<String> usedOptions = new ArrayList<>();
        Disguise disguise = null;
        DisguisePerm disguisePerm;

        if (args[0].startsWith("@")) {
            if (sender.hasPermission("libsdisguises.disguise.disguiseclone")) {
                disguise = DisguiseUtilities.getClonedDisguise(args[0].toLowerCase());

                if (disguise == null) {
                    throw new DisguiseParseException(LibsMsg.PARSE_NO_REF, args[0]);
                }
            } else {
                throw new DisguiseParseException(LibsMsg.PARSE_NO_PERM_REF);
            }

            disguisePerm = new DisguisePerm(disguise.getType());

            if (disguisePerm.isUnknown()) {
                throw new DisguiseParseException(LibsMsg.PARSE_CANT_DISG_UNKNOWN);
            }

            if (disguisePerm.getEntityType() == null) {
                throw new DisguiseParseException(LibsMsg.PARSE_CANT_LOAD);
            }

            if (!permissions.isAllowedDisguise(disguisePerm)) {
                throw new DisguiseParseException(LibsMsg.NO_PERM_DISGUISE);
            }
        } else {
            disguisePerm = getDisguisePerm(args[0]);
            Entry<String, Disguise> customDisguise = DisguiseConfig.getCustomDisguise(args[0]);

            if (customDisguise != null) {
                disguise = customDisguise.getValue().clone();
            }

            if (disguisePerm == null) {
                throw new DisguiseParseException(LibsMsg.PARSE_DISG_NO_EXIST, args[0]);
            }

            if (disguisePerm.isUnknown()) {
                throw new DisguiseParseException(LibsMsg.PARSE_CANT_DISG_UNKNOWN);
            }

            if (disguisePerm.getEntityType() == null) {
                throw new DisguiseParseException(LibsMsg.PARSE_CANT_LOAD);
            }

            if (!permissions.isAllowedDisguise(disguisePerm)) {
                throw new DisguiseParseException(LibsMsg.NO_PERM_DISGUISE);
            }

            HashMap<String, Boolean> disguiseOptions = getDisguiseOptions(sender, permNode, disguisePerm);

            if (disguise == null) {
                if (disguisePerm.isPlayer()) {
                    // If he is doing a player disguise
                    if (args.length == 1) {
                        // He needs to give the player name
                        throw new DisguiseParseException(LibsMsg.PARSE_SUPPLY_PLAYER);
                    } else {
                        // If they can't use this name, throw error
                        if (!hasPermissionOption(disguiseOptions, args[1].toLowerCase())) {
                            throw new DisguiseParseException(LibsMsg.PARSE_NO_PERM_NAME);
                        }

                        args[1] = args[1].replace("\\_", " ");

                        // Construct the player disguise
                        disguise = new PlayerDisguise(ChatColor.translateAlternateColorCodes('&', args[1]));
                        toSkip++;
                    }
                } else if (disguisePerm.isMob()) { // Its a mob, use the mob constructor
                    boolean adult = true;

                    if (args.length > 1) {
                        if (args[1].equalsIgnoreCase(TranslateType.DISGUISE_OPTIONS.get("baby")) ||
                                args[1].equalsIgnoreCase(TranslateType.DISGUISE_OPTIONS.get("adult"))) {
                            usedOptions.add("setbaby");
                            doCheck(sender, permissions, disguisePerm, usedOptions);
                            adult = args[1].equalsIgnoreCase(TranslateType.DISGUISE_OPTIONS.get("adult"));

                            toSkip++;
                        }
                    }

                    disguise = new MobDisguise(disguisePerm.getType(), adult);
                } else if (disguisePerm.isMisc()) {
                    // Its a misc, we are going to use the MiscDisguise constructor.
                    ItemStack itemStack = new ItemStack(Material.STONE);
                    int miscId = -1;

                    if (args.length > 1) {
                        switch (disguisePerm.getType()) {
                            case FALLING_BLOCK:
                            case DROPPED_ITEM:
                                Material material = null;

                                for (Material mat : Material.values()) {
                                    if (!mat.name().replace("_", "").equalsIgnoreCase(args[1].replace("_", ""))) {
                                        continue;
                                    }

                                    material = mat;
                                    break;
                                }

                                if (material == null) {
                                    break;
                                }

                                itemStack = new ItemStack(material);

                                if (!hasPermissionOption(disguiseOptions, itemStack.getType().name().toLowerCase())) {
                                    throw new DisguiseParseException(LibsMsg.PARSE_NO_PERM_PARAM,
                                            itemStack.getType().name(), disguisePerm.toReadable());
                                }

                                toSkip++;

                                if (disguisePerm.getType() == DisguiseType.FALLING_BLOCK) {
                                    usedOptions.add("setblock");
                                } else {
                                    usedOptions.add("setitemstack");
                                }

                                doCheck(sender, permissions, disguisePerm, usedOptions);
                                break;
                            case PAINTING:
                            case SPLASH_POTION:
                                if (!isInteger(args[1])) {
                                    break;
                                }

                                miscId = Integer.parseInt(args[1]);
                                toSkip++;

                                if (!hasPermissionOption(disguiseOptions, miscId + "")) {
                                    throw new DisguiseParseException(LibsMsg.PARSE_NO_PERM_PARAM, miscId + "",
                                            disguisePerm.toReadable());
                                }

                                if (disguisePerm.getType() == DisguiseType.PAINTING) {
                                    usedOptions.add("setpainting");
                                } else {
                                    usedOptions.add("setpotionid");
                                }

                                doCheck(sender, permissions, disguisePerm, usedOptions);
                                break;
                            default:
                                break;
                        }
                    }

                    // Construct the disguise
                    if (disguisePerm.getType() == DisguiseType.DROPPED_ITEM ||
                            disguisePerm.getType() == DisguiseType.FALLING_BLOCK) {
                        disguise = new MiscDisguise(disguisePerm.getType(), itemStack);
                    } else {
                        disguise = new MiscDisguise(disguisePerm.getType(), miscId);
                    }
                }
            }
        }

        // Copy strings to their new range
        String[] newArgs = new String[args.length - toSkip];
        System.arraycopy(args, toSkip, newArgs, 0, args.length - toSkip);

        callMethods(sender, disguise, permissions, disguisePerm, usedOptions, newArgs);

        // Alright. We've constructed our disguise.
        return disguise;
    }

    public static void callMethods(CommandSender sender, Disguise disguise, DisguisePermissions disguisePermission,
            DisguisePerm disguisePerm, Collection<String> usedOptions,
            String[] args) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException,
            DisguiseParseException {
        Method[] methods = ParamInfoManager.getDisguiseWatcherMethods(disguise.getWatcher().getClass());
        List<String> list = new ArrayList<>(Arrays.asList(args));

        for (int argIndex = 0; argIndex < args.length; argIndex++) {
            // This is the method name they provided
            String methodNameProvided = list.remove(0);
            // Translate the name they provided, to a name we recognize
            String methodNameJava = TranslateType.DISGUISE_OPTIONS.reverseGet(methodNameProvided);
            // The method we'll use
            Method methodToUse = null;
            Object valueToSet = null;
            DisguiseParseException parseException = null;

            for (Method method : methods) {
                if (!method.getName().equalsIgnoreCase(methodNameJava)) {
                    continue;
                }

                Class paramType = method.getParameterTypes()[0];

                ParamInfo paramInfo = ParamInfoManager.getParamInfo(paramType);

                try {
                    // Store how many args there were before calling the param
                    int argCount = list.size();

                    if (argCount < paramInfo.getMinArguments()) {
                        throw new DisguiseParseException(LibsMsg.PARSE_NO_OPTION_VALUE,
                                TranslateType.DISGUISE_OPTIONS.reverseGet(method.getName()));
                    }

                    valueToSet = paramInfo.fromString(list);

                    if (valueToSet == null && !paramInfo.canReturnNull()) {
                        throw new IllegalStateException();
                    }

                    // Skip ahead as many args as were consumed on successful parse
                    argIndex += argCount - list.size();

                    methodToUse = method;
                    // We've found a method which will accept a valid value, break
                    break;
                }
                catch (DisguiseParseException ex) {
                    parseException = ex;
                }
                catch (Exception ignored) {
                    parseException = new DisguiseParseException(LibsMsg.PARSE_EXPECTED_RECEIVED,
                            paramInfo.getDescriptiveName(), list.isEmpty() ? null : list.get(0),
                            TranslateType.DISGUISE_OPTIONS.reverseGet(method.getName()));
                }
            }

            if (methodToUse == null) {
                if (parseException != null) {
                    throw parseException;
                }

                throw new DisguiseParseException(LibsMsg.PARSE_OPTION_NA, methodNameProvided);
            }

            if (!usedOptions.contains(methodToUse.getName().toLowerCase())) {
                usedOptions.add(methodToUse.getName().toLowerCase());
            }

            doCheck(sender, disguisePermission, disguisePerm, usedOptions);

            if (FlagWatcher.class.isAssignableFrom(methodToUse.getDeclaringClass())) {
                methodToUse.invoke(disguise.getWatcher(), valueToSet);
            } else {
                methodToUse.invoke(disguise, valueToSet);
            }
        }
    }
}
