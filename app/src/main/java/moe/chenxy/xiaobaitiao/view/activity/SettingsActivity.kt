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

package moe.chenxy.xiaobaitiao.view.activity

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.TwoStatePreference
import com.google.android.material.snackbar.Snackbar
import moe.chenxy.xiaobaitiao.R
import moe.chenxy.xiaobaitiao.utils.BackupUtil
import moe.chenxy.xiaobaitiao.utils.ChenUtils

const val SHELL_RESTART_SYSTEMUI = "killall com.android.systemui"
private const val ALIAS_ACTIVITY_NAME = "moe.chenxy.xiaobaitiao.SettingsActivityAlias"

private var isActivated = false

class SettingsActivity : AppCompatActivity() {

    @SuppressLint("WorldReadableFiles")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        theme.applyStyle(rikka.material.preference.R.style.ThemeOverlay_Rikka_Material3_Preference, true)
        setContentView(R.layout.settings_activity)

        // Adapt transparent navbar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.navigationBarColor = Color.TRANSPARENT


        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.setTitle(R.string.title_activity_settings)


        try {
            // getSharedPreferences will hooked by LSPosed and change xml file path to /data/misc/**
            // will not throw SecurityException
            getSharedPreferences("chen_main_settings", Context.MODE_WORLD_READABLE)
            isActivated = true
        } catch (exception: SecurityException) {
            isActivated = false
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            // getSharedPreferences will hooked by LSPosed and change xml file path to /data/misc/**
            // will not throw SecurityException
            getSharedPreferences("chen_main_settings", Context.MODE_WORLD_READABLE)
            isActivated = true
        } catch (exception: SecurityException) {
            isActivated = false
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private lateinit var backupLauncher: ActivityResultLauncher<String>
        private lateinit var restoreLauncher: ActivityResultLauncher<Array<String>>
        private lateinit var restoreItems: List<String>

        private fun setActivateStatus(isActivated: Boolean, preference: Preference) {
            if (!isActivated) {
                preference.title = resources.getString(R.string.deactivated)
                resources.getDrawable(R.drawable.ic_round_error_outline, null)?.let {
                    DrawableCompat.setTint(it, Color.RED)
                    preference.icon = it
                }
            }
        }

        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            if (preference is TwoStatePreference) {
                val category = preference.parent as PreferenceCategory
                when (category.key) {
                    "home_handle" -> showRebootSnackBar(R.string.may_need_reboot, SHELL_RESTART_SYSTEMUI)
                    // system_server 侧的 hook，只有重启整机（或重启系统框架）后才会生效
                    "navbar_attach" -> showRebootSnackBar(R.string.may_need_reboot_reboot_phone, null)
                }
                this.context?.let { ChenUtils.performVibrateHeavyClick(it) }
            }
            return super.onPreferenceTreeClick(preference)
        }

        override fun onResume() {
            super.onResume()
            findPreference<Preference>("activate_status")?.let {
                setActivateStatus(
                    isActivated,
                    it
                )
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = "chen_main_settings"
            setPreferencesFromResource(R.xml.chen_preferences, rootKey)

            findPreference<Preference>("activate_status")?.let {
                setActivateStatus(
                    isActivated,
                    it
                )
            }

            findPreference<Preference>("home_handle_anim_custom")?.intent = Intent(context, HomeHandleCustomActivity::class.java)

            findPreference<SwitchPreferenceCompat>("hide_app_icon")?.isChecked = !isLauncherIconVisible()

            findPreference<SwitchPreferenceCompat>("hide_app_icon")?.setOnPreferenceChangeListener { _, newValue ->
                setLauncherIconVisible(!(newValue as Boolean))
                return@setOnPreferenceChangeListener true
            }

            findPreference<Preference>("backup_config")?.setOnPreferenceClickListener { _ ->
                backupConfig()
                return@setOnPreferenceClickListener true
            }
            findPreference<Preference>("restore_config")?.setOnPreferenceClickListener { _ ->
                restoreConfig()
                return@setOnPreferenceClickListener true
            }

            findPreference<Preference>("about_author_github")?.intent =
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/JinHao0007"))
            findPreference<Preference>("about_author_origin")?.intent =
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Art-Chen/MIUI-Extra"))

            if (!this::backupLauncher.isInitialized) {
                backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
                    if (uri != null) {
                        BackupUtil.triggerBackupFile(uri, requireContext())
                    } else {
                        Snackbar.make(
                            requireActivity().findViewById(R.id.settings_root_layout),
                            R.string.backup_failed, Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }

                restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                    if (uri != null) {
                        BackupUtil.triggerRestoreFile(uri, requireContext(), restoreItems)
                        Intent(requireContext(), SettingsActivity::class.java).also {
                            it.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK
                            requireActivity().finish()
                            requireContext().startActivity(it)
                        }
                    } else {
                        Snackbar.make(
                            requireActivity().findViewById(R.id.settings_root_layout),
                            R.string.restore_failed, Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        private fun showRebootSnackBar(resId: Int?, shell: String?) {
            Snackbar.make(requireActivity().findViewById(R.id.settings_root_layout),
                resId ?: R.string.may_need_reboot, Snackbar.LENGTH_LONG)
                .setAction(R.string.reboot) { _ ->
                    ChenUtils.execShell(shell ?: "/system/bin/svc power reboot")
                }
                .show()
        }

        @SuppressLint("QueryPermissionsNeeded")
        private fun isLauncherIconVisible(): Boolean {
            val component = context?.let { ComponentName(it, ALIAS_ACTIVITY_NAME) }
            val manager = activity?.packageManager
            val intent = Intent().setComponent(component)

            val list: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                manager!!.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                manager!!.queryIntentActivities(
                    intent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )
            }

            return list.isNotEmpty()
        }

        private fun setLauncherIconVisible(visible: Boolean) {
            if (isLauncherIconVisible() == visible) return
            val component = context?.let { ComponentName(it, ALIAS_ACTIVITY_NAME) }
            val manager = activity?.packageManager
            val newState =
                if (visible) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (component != null) {
                manager?.setComponentEnabledSetting(component, newState, PackageManager.DONT_KILL_APP)
            }
        }

        private fun backupConfig() {
            backupLauncher.launch("Xiaobaitiao_configs_${System.currentTimeMillis()}.json")
        }

        private fun restoreConfig() {
            val context = requireContext()
            val restoreSelect = arrayOfNulls<Boolean>(1)

            val builder = AlertDialog.Builder(context).let {
                it.setTitle(R.string.restore)
                it.setMultiChoiceItems(R.array.restore_entries, null) { dialog, witch, b ->
                    restoreSelect[witch] = b
                }
                it.setPositiveButton(R.string.confirm) { dialog, witch ->
                    val nameList = mutableListOf<String>()
                    for (i in 0..restoreSelect.size - 1) {
                        if (restoreSelect[i] == true) {
                            nameList.add(BackupUtil.allPrefName[i])
                        }
                    }
                    restoreItems = nameList
                    restoreLauncher.launch(arrayOf<String>("application/json"))
                }
                it.setNegativeButton(R.string.cancel) { dialog, witch ->
                    // nothing
                }
            }

            builder.show()
        }
    }

}
