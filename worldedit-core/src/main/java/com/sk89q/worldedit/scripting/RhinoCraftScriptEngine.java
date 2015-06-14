/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.scripting;

import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.script.ScriptException;

import com.sk89q.worldedit.LocalConfiguration;
import com.sk89q.worldedit.WorldEdit;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrappedException;

import com.sk89q.worldedit.WorldEditException;
import org.mozilla.javascript.commonjs.module.ModuleScriptProvider;
import org.mozilla.javascript.commonjs.module.Require;
import org.mozilla.javascript.commonjs.module.RequireBuilder;
import org.mozilla.javascript.commonjs.module.provider.ModuleSourceProvider;
import org.mozilla.javascript.commonjs.module.provider.SoftCachingModuleScriptProvider;
import org.mozilla.javascript.commonjs.module.provider.UrlModuleSourceProvider;
import org.mozilla.javascript.tools.shell.Global;

public class RhinoCraftScriptEngine implements CraftScriptEngine {
    private int timeLimit;

    @Override
    public void setTimeLimit(int milliseconds) {
        timeLimit = milliseconds;
    }

    @Override
    public int getTimeLimit() {
        return timeLimit;
    }

    @Override
    public Object evaluate(FileReader file, String filename, Map<String, Object> args)
            throws ScriptException, Throwable {

        Global global = new Global();

        WorldEdit worldEdit = WorldEdit.getInstance();
        LocalConfiguration config = worldEdit.getConfiguration();
        File dir = worldEdit.getWorkingDirectoryFile(config.scriptsDir);

        List<URI> paths = Arrays.asList(
            dir.toURI()
        );

        RhinoContextFactory factory = new RhinoContextFactory(timeLimit);

        ModuleSourceProvider sourceProvider = new UrlModuleSourceProvider(paths, null);
        ModuleScriptProvider scriptProvider = new SoftCachingModuleScriptProvider(sourceProvider);
        RequireBuilder builder = new RequireBuilder();
        builder.setSandboxed(false);
        builder.setModuleScriptProvider(scriptProvider);

        global.init(factory);
        Context cx = factory.enterContext();
        cx.setLanguageVersion(170);

        ScriptableObject scope = new ImporterTopLevel(cx);
        Require require = builder.createRequire(cx, scope);
        require.install(scope);

        for (Map.Entry<String, Object> entry : args.entrySet()) {
            ScriptableObject.putProperty(scope, entry.getKey(),
                    Context.javaToJS(entry.getValue(), scope));
        }

        try {
            return cx.evaluateReader(scope, file, filename, 1, null);
        } catch (Error e) {
            throw new ScriptException(e.getMessage());
        } catch (RhinoException e) {
            if (e instanceof WrappedException) {
                Throwable cause = e.getCause();
                if (cause instanceof WorldEditException) {
                    throw cause;
                }
            }

            String msg;
            int line = (line = e.lineNumber()) == 0 ? -1 : line;

            if (e instanceof JavaScriptException) {
                msg = String.valueOf(((JavaScriptException) e).getValue());
            } else {
                msg = e.getMessage();
            }

            ScriptException scriptException =
                new ScriptException(msg, e.sourceName(), line);
            scriptException.initCause(e);

            throw scriptException;
        } finally {
            file.close();

            Context.exit();
        }
    }

}
