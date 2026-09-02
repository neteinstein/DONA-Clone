package com.neteinstein.donaclone.core.data.mapper

import com.neteinstein.donaclone.core.database.house.HouseEntity
import com.neteinstein.donaclone.core.model.House

fun HouseEntity.toDomain(): House = House(
    name = name,
    dns = dns,
    secureDns = secureDns,
    localIp = localIp,
    secureLocalIp = secureLocalIp,
    username = username,
    password = password,
    stayConnected = stayConnected,
    notificationId = notificationId,
    codeOnDisarmAlarm = codeOnDisarmAlarm,
)

fun House.toEntity(): HouseEntity = HouseEntity(
    name = name,
    dns = dns,
    secureDns = secureDns,
    localIp = localIp,
    secureLocalIp = secureLocalIp,
    username = username,
    password = password,
    stayConnected = stayConnected,
    notificationId = notificationId,
    codeOnDisarmAlarm = codeOnDisarmAlarm,
)
