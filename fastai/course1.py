'''
Author: zhuqingquan zqq_222@163.com
Date: 2023-11-15 
FilePath: /fastai/course1.py
Description: 通过fastai课程学习深度学习编程的第一课，可能还包含一些学习过程中的测试代码
'''
# 验证pytorch是否正常安装，for cuda
import torch
torch.cuda.is_available()

from fastai.vision.all import *

def  is_cat(x): return x[0].isupper()
def learnCat():
    # URLS.PETS = https://s3.amazonaws.com/fast-ai-imageclas/oxford-iiit-pet.tgz
    URLs.PETS_LOCAL = './dataset/oxford-iiit-pet.tgz'
    # 如果下载并解压成功则会放在~/.fastai/data/oxford-iiit-pet下
    path = untar_data(URLs.PETS, base='./.fastai')/'images'

    dls = ImageDataLoaders.from_name_func(path, get_image_files(path), valid_pct=0.2, seed=42, label_func=is_cat, item_tfms=Resize(224))

    learn = vision_learner(dls, resnet34, metrics=error_rate)
    learn.fine_tune(1)
    return learn

def predictCat(learn : Learner):
    uploader = SimpleNamespace(data=['data_for_test/R-C.jpeg'])
    img = PILImage.create(uploader.data[0])
    is_cat,_,probs = learn.predict(img)
    print(f"Is this a cat?: {is_cat}")
    print(f"Probability it's a cat: {probs[1].item():.6f}")
    
if __name__=='__main__':
    learn = learnCat()
    predictCat(learn)