/*
 * Xiaobaitiao - a standalone port of the home handle (navigation bar)
 * module of MIUI-Extra <https://github.com/Art-Chen/MIUI-Extra>.
 *
 * Original work Copyright (C) Art_Chen.
 * This file has been modified for the standalone port: package renamed,
 * features unrelated to the home handle removed, and HyperOS 4 support
 * added. The list of changes is documented in the README of this project.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version. See the LICENSE file for the full text.
 */

package moe.chenxy.xiaobaitiao.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import moe.chenxy.xiaobaitiao.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader

object BackupUtil {
    const val PREF_MAIN_SETTINGS = "chen_main_settings"

    val allPrefName = mutableListOf(PREF_MAIN_SETTINGS)


    @SuppressLint("WorldReadableFiles")
    fun createJsonByPref(name: String, context: Context) : JSONObject {
        val ret = JSONObject()
        val content: JSONObject
        val pref = context.getSharedPreferences(name, Context.MODE_WORLD_READABLE)

        ret.put("name", name)
        content = JSONObject(pref.all)
        ret.put("content", content)

        return ret
    }

    fun triggerBackupFile(uri: Uri, context: Context) {
        val rootArray = JSONArray()
        for (name in allPrefName) {
            rootArray.put(createJsonByPref(name, context))
        }
        context.contentResolver.openFileDescriptor(uri, "w")?.use {
            FileOutputStream(it.fileDescriptor).use {
                it.write(rootArray.toString().toByteArray())
            }
        }
    }

    @SuppressLint("WorldReadableFiles")
    fun triggerRestoreFile(uri: Uri, context: Context, perfNames: List<String>) {
        Log.i("Art_Chen", "restore $perfNames")
        // Read json first
        val stringBuilder = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    stringBuilder.append(line)
                    line = reader.readLine()
                }
            }
        }

        val jsonArray = JSONArray(stringBuilder.toString())

        for (pName in perfNames) {
            var jsonObj: JSONObject? = null
            for (i in 0..jsonArray.length() - 1) {
                jsonObj = jsonArray.get(i) as JSONObject
                // max O(n2), but works.
                if (jsonObj.get("name") == pName) break
            }
            if (jsonObj == null) {
                Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_LONG).show()
                return
            }

            val prefEditor = context.getSharedPreferences(pName, Context.MODE_WORLD_READABLE).edit()
            val content = jsonObj.getJSONObject("content")
            val keys = content.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                var value = content.get(key)
                Log.d("Art_Chen", "restoring $key, $value")

                when (value) {
                    is String -> {
                        if (value.contains("[") && value.contains("]")) {
                            value = value.replace("[", "").replace("]", "").replace(" ", "")
                            val array = value.split(",")
                            val stringSet = array.toSet()
                            prefEditor.putStringSet(key, stringSet)
                        } else {
                            prefEditor.putString(key, value)
                        }
                    }
                    is JSONArray -> {
                        val set = mutableSetOf<String>()
                        for (i in 0..value.length() - 1) {
                            set.add(value.getString(i))
                        }
                        prefEditor.putStringSet(key, set)
                    }
                    is Boolean -> prefEditor.putBoolean(key, value)
                    is Int -> prefEditor.putInt(key, value)
                }
            }
            prefEditor.apply()
        }
        Toast.makeText(context, R.string.restore_success, Toast.LENGTH_SHORT).show()
    }
}
