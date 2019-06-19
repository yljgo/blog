#!/bin/sh

cd `dirname $0`

# Java主程序，也就是main(String[] args)方法类
APP_MAIN=com.yibi.ocs.Application

# 初始化全局变量，用于标识交易前置系统的PID（0表示未启动）
tradePortalPID=0

# 获取Java应用的PID
# ------------------------------------------------------------------------------------------------------
# 说明：通过JDK自带的jps命令，联合Linux中的grep命令，可以准确查找到Java应用的PID
#       [jps -l]表示显示Java主程序的完整包路径
#       awk命令可以分割出PID（$1部分）及Java主程序名称（$2部分）
# 例子：[$JAVA_HOME/bin/jps -l | grep $APP_MAIN]命令执行，会看到[5775 com.cucpay.tradeportal.MainApp]
# 另外：这个命令也可以取到程序的PID-->[ps aux|grep java|grep $APP_MAIN|grep -v grep|awk '{print $2}']
# ------------------------------------------------------------------------------------------------------
getTradeProtalPID(){
    javaps=`$JAVA_HOME/bin/jps -l | grep $APP_MAIN`
    if [ -n "$javaps" ]; then
        tradePortalPID=`echo $javaps | awk '{print $1}'`
    else
        tradePortalPID=0
    fi
}

# 停止Java应用程序
# ------------------------------------------------------------------------------------------------------
shutdown(){
    getTradeProtalPID
    echo -n "Stopping $APP_MAIN ..."
    kill $tradePortalPID
    sleep 3
    getTradeProtalPID
    if [ $tradePortalPID -eq 0 ]; then
        echo "[Success]"
        echo "==============================================================================================="
    else
        echo "[Failed]"
        echo "==============================================================================================="
    fi
}

# 调用停止命令
shutdown