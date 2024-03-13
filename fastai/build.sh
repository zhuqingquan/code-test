# install pytorch for cuda 12.2
pip install torch torchvision torchaudio -f https://download.pytorch.org/whl/cu122/torch_stable.html
# install pytorch for cuda 12.1, using  tsinghua mirror source
pip install torch torchvision torchaudio -i https://pypi.tuna.tsinghua.edu.cn/simple
# 通过conda安装12.1版本cuda支持的pytorch，测试这个用于fastai可以。上面通过pip install安装的会有点问题
conda install pytorch torchvision torchaudio pytorch-cuda=12.1 -c pytorch -c nvidia   #可用

pip install fastai