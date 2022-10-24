import os, re
import os.path

def exeCmd(cmd):
    r = os.popen(cmd)
    lines = r.readlines()
    #text = r.read()
    r.close()
    return lines

def getDirNameFromDevices(lines):
    dirNames = []
    for line in lines:
        print("l: %s" %(line))
        index = line.find("model:")
        if(index!=-1):
            end = line.find(' ', index)
            strModel = line[index+len("model:"):end]
            print("model: %s" %(strModel))
            strUUID  = line[0:line.find(' ')]
            print("UUID: %s" %(strUUID))
            if(len(strUUID)>0 and len(strModel)):
                strDir = "{model}-{uuid}".format(model=strModel, uuid=strUUID)
                print(strDir)
                dirNames.append(strDir)
    return dirNames

def makesureDirExist(dirNames):
    for strdir in dirNames:
        print(strdir)
        if not os.path.isdir(strdir):
            os.mkdir(strdir)
            #dirPath = os.path

class DirElem:
    srcDir = ""
    dstDir = ""

    def __init__(self, srcDir, dstDir):
        self.srcDir = srcDir 
        self.dstDir = dstDir


if __name__ == '__main__':
    cmd = 'adb devices -l'
    # use adb devices cmd to get adr phone list that connected to this compute
    result = exeCmd(cmd)
    print("result = %s" %(result))
    # get directory name for every phone in the phone list to save file
    dirNames = getDirNameFromDevices(result)
    # makesure the directory was created, if not, create it.
    makesureDirExist(dirNames)
    dirElemsCopy = { DirElem("/sdcard/DCIM/Screenshots", "DCIM/"),
                     DirElem("/sdcard/BaiduNetdisk", ""),
    }
    for strdir in dirNames:
        # cmd_pull = "adb pull {dirInPhone} {dirDst}".format(dirInPhone="/sdcard/DCIM", dirDst=strdir)
        for dirElem in dirElemsCopy:
            print("Start ---->")
            dirDst="{dirForPhone}/{dDir}".format(dirForPhone=strdir, dDir=dirElem.dstDir)
            cmd_pull = "adb pull {dirInPhone} {dirDst}".format(dirInPhone=dirElem.srcDir, dirDst=dirDst)
            print(cmd_pull)
            exeCmd(cmd_pull)
            print("End  <----")
