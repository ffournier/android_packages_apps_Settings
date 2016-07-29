/*
 * Copyright (C) 2010 The Android-x86 Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.ethernet;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.NetworkUtils;
import android.net.DhcpInfo;
import android.net.ethernet.EthernetManager;
import android.net.ethernet.EthernetDevInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Slog;

import com.android.settings.R;
import com.android.settings.Utils;

import java.util.List;

public class EthernetConfigDialog extends AlertDialog implements
        DialogInterface.OnClickListener, DialogInterface.OnShowListener,
        DialogInterface.OnDismissListener {
    private final String TAG = "EthConfDialog";
    private static final boolean localLOGV = false;

    /* This value comes from "wifi_ip_settings" resource array */
    private static final int DHCP = 0;
    private static final int STATIC_IP = 1;

    private View mView;
    private Spinner mDevList;
    private TextView mDevs;
    private Spinner mIpSettingsSpinner;
    private CheckBox mKeepOn;
    private RadioButton mConTypeDhcp;
    private RadioButton mConTypeManual;
    private EditText mIpaddr;
    private EditText mDns;
    private EditText mGw;
    private EditText mMask;
    private TextWatcher mWatcher;

    private EthernetLayer mEthLayer;
    private EthernetManager mEthManager;
    private EthernetDevInfo mEthInfo;
    private boolean mEnablePending;
    private boolean mChangedType, mChangedKeep, mChangedIp;
    private boolean mTypeManual;

    private Context mContext;

    public EthernetConfigDialog(Context context, EthernetManager ethManager) {
        super(context);
        mEthManager = ethManager;
        mEthLayer = new EthernetLayer(this, ethManager);
        mChangedType = mChangedKeep = mChangedIp = false;
        mContext = context;
        buildDialogContent(context);
        setOnShowListener(this);
        setOnDismissListener(this);
        enableAfterConfig();
    }

    public void onShow(DialogInterface dialog) {
        if (localLOGV) Slog.d(TAG, "onShow");
        mEthLayer.resume();
        // soft keyboard pops up on the disabled EditText. Hide it.
        if (getCurrentFocus() != null) {
            InputMethodManager imm = (InputMethodManager)mContext.getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
        if (localLOGV) Slog.d(TAG, "Device added: " + mEthManager.isEthDeviceAdded());
        if (mEthInfo == null ||
            mEthInfo.getConnectMode().equals(EthernetDevInfo.ETH_CONN_MODE_DHCP)) {
            if (mEthManager.isEthDeviceAdded()) {
                DhcpInfo dhcpInfo = mEthManager.getDhcpInfo();
                mIpaddr.setText(getAddress(dhcpInfo.ipAddress));
                mMask.setText(getAddress(dhcpInfo.netmask));
                mGw.setText(getAddress(dhcpInfo.gateway));
                mDns.setText(getAddress(dhcpInfo.dns1));
                mView.findViewById(R.id.eth_ip_fields).setVisibility(View.VISIBLE);
            } else {
                mView.findViewById(R.id.eth_ip_fields).setVisibility(View.GONE);
            }
        }
        setSaveButton();
    }

    public void onDismiss(DialogInterface dialog) {
        if (localLOGV) Slog.d(TAG, "onDismiss");
        mEthLayer.pause();
    }

    private static String getAddress(int addr) {
        return NetworkUtils.intToInetAddress(addr).getHostAddress();
    }

    private void setSaveButton() {
        Button button = getButton(BUTTON_POSITIVE);
        if (button != null)
            button.setEnabled(mChangedType || mChangedKeep || (mTypeManual && mChangedIp));
    }

    public int buildDialogContent(Context context) {
        this.setTitle(R.string.eth_config_title);
        this.setView(mView = getLayoutInflater().inflate(R.layout.eth_configure, null));
        mDevs = (TextView) mView.findViewById(R.id.eth_dev_list_text);
        mDevList = (Spinner) mView.findViewById(R.id.eth_dev_spinner);
        mIpSettingsSpinner = (Spinner) mView.findViewById(R.id.eth_ip_method);
        mKeepOn = ((CheckBox) mView.findViewById(R.id.eth_keep_on));
        mConTypeDhcp = (RadioButton) mView.findViewById(R.id.dhcp_radio);
        mConTypeManual = (RadioButton) mView.findViewById(R.id.manual_radio);
        mIpaddr = (EditText)mView.findViewById(R.id.ipaddr_edit);
        mMask = (EditText)mView.findViewById(R.id.netmask_edit);
        mDns = (EditText)mView.findViewById(R.id.eth_dns_edit);
        mGw = (EditText)mView.findViewById(R.id.eth_gw_edit);

        mConTypeDhcp.setChecked(true);
        mConTypeManual.setChecked(false);
        mIpSettingsSpinner.setSelection(DHCP);
        mKeepOn.setChecked(false);
        mIpaddr.setEnabled(false);
        mMask.setEnabled(false);
        mDns.setEnabled(false);
        mGw.setEnabled(false);

        /*
        mConTypeManual.setOnClickListener(new RadioButton.OnClickListener() {
            public void onClick(View v) {
                if (mEthInfo.getConnectMode().equals(EthernetDevInfo.ETH_CONN_MODE_DHCP)) {
                    if (localLOGV) Slog.d(TAG, "Manual  IP: " + mEthInfo.getIpAddress());
                    if (mEthInfo.getIpAddress() != null) {
                        mIpaddr.setText(mEthInfo.getIpAddress());
                        mMask.setText(mEthInfo.getNetMask());
                        mGw.setText(mEthInfo.getRouteAddr());
                        mDns.setText(mEthInfo.getDnsAddr());
                    }
                }
                mView.findViewById(R.id.eth_ip_fields).setVisibility(View.VISIBLE);
                mIpaddr.setEnabled(true);
                mDns.setEnabled(true);
                mGw.setEnabled(true);
                mMask.setEnabled(true);
                getButton(BUTTON_POSITIVE).setEnabled(true);
            }
        });

        mConTypeDhcp.setOnClickListener(new RadioButton.OnClickListener() {
            public void onClick(View v) {
                mIpaddr.setEnabled(false);
                mDns.setEnabled(false);
                mGw.setEnabled(false);
                mMask.setEnabled(false);
                if (mEthInfo.getConnectMode().equals(EthernetDevInfo.ETH_CONN_MODE_DHCP)) {
                    DhcpInfo dhcpInfo = mEthManager.getDhcpInfo();
                    if (localLOGV) Slog.d(TAG, "Automic IP: " + getAddress(dhcpInfo.ipAddress));
                    mIpaddr.setText(getAddress(dhcpInfo.ipAddress));
                    mMask.setText(getAddress(dhcpInfo.netmask));
                    mGw.setText(getAddress(dhcpInfo.gateway));
                    mDns.setText(getAddress(dhcpInfo.dns1));
                    int vis = mEthManager.isEthDeviceAdded() ? View.VISIBLE : View.GONE;
                    mView.findViewById(R.id.eth_ip_fields).setVisibility(vis);
                    getButton(BUTTON_POSITIVE).setEnabled(false);
                } else {
                    mView.findViewById(R.id.eth_ip_fields).setVisibility(View.GONE);
                    getButton(BUTTON_POSITIVE).setEnabled(true);
                }
            }
        });
        */

        this.setInverseBackgroundForced(true);
        this.setButton(BUTTON_POSITIVE, context.getText(R.string.menu_save), this);
        this.setButton(BUTTON_NEGATIVE, context.getText(R.string.menu_cancel), this);
        String[] Devs = mEthManager.getDeviceNameList();
        updateDevNameList(Devs);
        if (Devs != null) {
            if (mEthManager.isEthConfigured()) {
                String propties = Utils.getEtherProperties(mContext);
                Slog.d(TAG, "Properties: " + propties);

                mEthInfo = mEthManager.getSavedEthConfig();
                for (int i = 0 ; i < Devs.length; i++) {
                    if (Devs[i].equals(mEthInfo.getIfName())) {
                        mDevList.setSelection(i);
                        break;
                    }
                }

                if (mEthInfo.getConnectMode().equals(EthernetDevInfo.ETH_CONN_MODE_DHCP)) {
                    mIpaddr.setEnabled(false);
                    mDns.setEnabled(false);
                    mGw.setEnabled(false);
                    mMask.setEnabled(false);
                    if (mEthManager.isEthDeviceAdded()) {
                        DhcpInfo dhcpInfo = mEthManager.getDhcpInfo();
                        mIpaddr.setText(getAddress(dhcpInfo.ipAddress));
                        mMask.setText(getAddress(dhcpInfo.netmask));
                        mGw.setText(getAddress(dhcpInfo.gateway));
                        mDns.setText(getAddress(dhcpInfo.dns1));
                        mView.findViewById(R.id.eth_ip_fields).setVisibility(View.VISIBLE);
                    } else {
                        mView.findViewById(R.id.eth_ip_fields).setVisibility(View.GONE);
                    }
                } else {
                    mIpaddr.setText(mEthInfo.getIpAddress());
                    mMask.setText(mEthInfo.getNetMask());
                    mGw.setText(mEthInfo.getRouteAddr());
                    mDns.setText(mEthInfo.getDnsAddr());
                    mConTypeDhcp.setChecked(false);
                    mConTypeManual.setChecked(true);
                    mIpSettingsSpinner.setSelection(STATIC_IP);
                    mTypeManual = true;
                    mIpaddr.setEnabled(true);
                    mDns.setEnabled(true);
                    mGw.setEnabled(true);
                    mMask.setEnabled(true);
                }

                mKeepOn.setChecked(mEthInfo.getAlwaysOn() > 0);
            }
        }

        mIpSettingsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int methodInConfig = DHCP;
                if (mEthInfo != null &&
                    mEthInfo.getConnectMode().equals(EthernetDevInfo.ETH_CONN_MODE_MANUAL)) {
                    methodInConfig = STATIC_IP;
                }

                mChangedType = (position != methodInConfig);
                mTypeManual = (position == STATIC_IP);

                int vis = ((methodInConfig == DHCP && mEthManager.isEthDeviceAdded())
                           || mTypeManual) ? View.VISIBLE : View.GONE;
                mView.findViewById(R.id.eth_ip_fields).setVisibility(vis);

                setSaveButton();
                mIpaddr.setEnabled(mTypeManual);
                mDns.setEnabled(mTypeManual);
                mGw.setEnabled(mTypeManual);
                mMask.setEnabled(mTypeManual);

                if (mChangedType && methodInConfig == DHCP) {
                    if (mTypeManual) {
                        if (localLOGV) Slog.d(TAG, "Manual  IP: " + mEthInfo.getIpAddress());
                        if (mEthInfo != null && mEthInfo.getIpAddress() != null) {
                            mIpaddr.setText(mEthInfo.getIpAddress());
                            mMask.setText(mEthInfo.getNetMask());
                            mGw.setText(mEthInfo.getRouteAddr());
                            mDns.setText(mEthInfo.getDnsAddr());
                        }
                    } else {
                        DhcpInfo dhcpInfo = mEthManager.getDhcpInfo();
                        if (localLOGV) Slog.d(TAG, "Automic IP: " + getAddress(dhcpInfo.ipAddress));
                        mIpaddr.setText(getAddress(dhcpInfo.ipAddress));
                        mMask.setText(getAddress(dhcpInfo.netmask));
                        mGw.setText(getAddress(dhcpInfo.gateway));
                        mDns.setText(getAddress(dhcpInfo.dns1));
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                //
            }
        });

        mKeepOn.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (localLOGV) Slog.d(TAG, "onCheckedChanged: " + isChecked);
                boolean prevStatus = false;
                if (mEthInfo != null && (mEthInfo.getAlwaysOn() > 0))
                    prevStatus = true;
                mChangedKeep = (prevStatus != isChecked);
                setSaveButton();
            }
        });

        mWatcher = new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (localLOGV) Slog.d(TAG, "afterTextChanged: " + s);
                mChangedIp = true;
                setSaveButton();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // work done in afterTextChanged
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // work done in afterTextChanged
            }
        };
        mIpaddr.addTextChangedListener(mWatcher);
        mMask.addTextChangedListener(mWatcher);
        mDns.addTextChangedListener(mWatcher);
        mGw.addTextChangedListener(mWatcher);

        return 0;
    }

    private void handle_saveconf() {
        String selected = null;
        if (mDevList.getSelectedItem() != null)
            selected = mDevList.getSelectedItem().toString();
        if (selected == null || selected.isEmpty())
            return;
        EthernetDevInfo info = new EthernetDevInfo();
        info.setIfName(selected);
        info.setAlwaysOn(mKeepOn.isChecked() ? 1 : 0);
        if (localLOGV)
            Slog.v(TAG, "Config device for " + selected);
        if (/* mConTypeDhcp.isChecked() || */
            mIpSettingsSpinner.getSelectedItemPosition() == DHCP) {
            Slog.i(TAG, "mode dhcp");
            info.setConnectMode(EthernetDevInfo.ETH_CONN_MODE_DHCP);
            // Keep the last IP settings for switching back to manual mode
            if (mEthInfo != null) {
                info.setIpAddress(mEthInfo.getIpAddress());
                info.setRouteAddr(mEthInfo.getRouteAddr());
                info.setDnsAddr(mEthInfo.getDnsAddr());
                info.setNetMask(mEthInfo.getNetMask());
            }
        } else {
            Slog.i(TAG, "mode manual");
            if (isIpAddress(mIpaddr.getText().toString(), true)
                    && isIpAddress(mMask.getText().toString(), true)
                    && isIpAddress(mGw.getText().toString(), false)
                    && isIpAddress(mDns.getText().toString(), false)) {
                info.setConnectMode(EthernetDevInfo.ETH_CONN_MODE_MANUAL);
                info.setIpAddress(mIpaddr.getText().toString());
                info.setRouteAddr(mGw.getText().toString());
                info.setDnsAddr(mDns.getText().toString());
                info.setNetMask(mMask.getText().toString());
            } else {
                Toast.makeText(mContext, R.string.eth_settings_error, Toast.LENGTH_LONG).show();
                return;
            }
        }
        mEthManager.updateEthDevInfo(info);
        if (localLOGV) Slog.i(TAG, "mEnablePending is " + mEnablePending);
        if (mEnablePending) {
            if (localLOGV) Slog.i(TAG, "mEthManager.getEthState() is " + mEthManager.getEthState());
            if (mEthManager.getEthState() == mEthManager.ETH_STATE_ENABLED) {
                mEthManager.setEthEnabled(true);
            }
            mEnablePending = false;
        }
        mEthInfo = info;
        mChangedType = mChangedKeep = mChangedIp = false;
    }

    private boolean isIpAddress(String value, boolean force) {
        int start = 0;
        int end = value.indexOf('.');
        int numBlocks = 0;

        if (value.length() == 0 && !force)
            return true;

        while (start < value.length()) {
            if (end == -1) {
                end = value.length();
            }

            try {
                int block = Integer.parseInt(value.substring(start, end));
                if ((block > 255) || (block < 0)) {
                        return false;
                }
            } catch (NumberFormatException e) {
                    return false;
            }

            numBlocks++;

            start = end + 1;
            end = value.indexOf('.', start);
        }
        return numBlocks == 4;
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case BUTTON_POSITIVE:
                handle_saveconf();
                break;
            case BUTTON_NEGATIVE:
                //Don't need to do anything
                break;
            default:
        }
    }

    public void updateDevNameList(String[] DevList) {
        if (DevList == null) {
            DevList = new String[] {};
        }
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(
                getContext(), android.R.layout.simple_spinner_item, DevList);
        adapter.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        mDevList.setAdapter(adapter);
    }

    public void enableAfterConfig() {
        mEnablePending = true;
    }
}
