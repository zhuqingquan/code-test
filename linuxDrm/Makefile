modeset : modeset.c
	gcc -o modeset modeset.c -I/usr/include/drm -ldrm -g

testDrm : testDrm.cpp
	g++ -o testDrm testDrm.cpp

printModes : printModes.c
	gcc -o printModes printModes.c -I/usr/include/drm -ldrm -g

eglCapture : eglCapture.cpp 
	gcc -o eglCapture eglCapture.cpp ../libs/glad/src/egl.c ../libs/glad/src/gl.c -I/usr/include/drm -I../libs/glad/include -ldrm -lX11 -g
