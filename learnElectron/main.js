const { app, BrowserWindow, Menu} = require('electron')
// include the Node.js 'path' module at the top of your file
const path = require('node:path')

const createWindow = () => {
    Menu.setApplicationMenu(null)
    const win = new BrowserWindow({
        width : 1280,
        height : 720,
        fullscreen : true,
        //menuBarVisible : false,
        webPreferences: {
            preload: path.join(__dirname, 'preload.js')
        }
    })

    win.webContents.openDevTools()
    win.loadURL("https://livecdn.jinsemengxiang.cn/mytest_tmeta_web/live/index.html#/")
    //win.loadFile('index.html')
}

app.whenReady().then( ()=>{
    createWindow()
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})