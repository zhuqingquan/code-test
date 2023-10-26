#include <stdio.h>
#include <cuda_runtime.h>


__global__ void vectorAdd(const float* A, const float* B, float* C, int numElements)
{
    int i = blockDim.x * blockIdx.x + threadIdx.x;

    if( i < numElements)
    {
        C[i] = A[i] + B[i];
    }
}

int main()
{
    printf("hello cuda. vector add\n");
    int numElementCount = 50000;
    int byteCount = numElementCount * sizeof(float);
    printf("malloc for A, B, C. size=%d\n", byteCount);
    float* pA = (float*)malloc(byteCount);
    float* pB = (float*)malloc(byteCount);
    float* pC = (float*)malloc(byteCount);
    for(int i=0; i<numElementCount; i++)
    {
        pA[i] = rand() / (float)RAND_MAX;
        pB[i] = rand() / (float)RAND_MAX;
    }

    printf("cudaMalloc for A, B, C. size=%d\n", byteCount);
    float* pA_d = NULL;
    cudaError_t err = cudaMalloc((void**)&pA_d, byteCount);
    if(err!=cudaSuccess)
    {
        fprintf(stderr, "Failed to allocate device memory for A. error code %d, error: %s\n",
                err, cudaGetErrorString(err));
        return -1;
    }
    float* pB_d = nullptr;
    err = cudaMalloc((void**)&pB_d, byteCount);
    if(err!=cudaSuccess)
    {
        fprintf(stderr, "Failed to allocate device memory for B. error code %d, error: %s\n",
                err, cudaGetErrorString(err));
        return -1;
    }
    float* pC_d = nullptr;
    err = cudaMalloc((void**)&pC_d, byteCount);
    if(err!=cudaSuccess)
    {
        fprintf(stderr, "Failed to allocate device memory for C. error code %d, error: %s\n",
                err, cudaGetErrorString(err));
        return -1;
    }

    printf("copy A, B vector data from host memory to device memory\n");
    err = cudaMemcpy(pA_d, pA, byteCount, cudaMemcpyHostToDevice);
    if(err!=cudaSuccess)
    {
        fprintf(stderr, "Failed to copy A from host memory to device memory. error code=%d msg:%s\n",
                err, cudaGetErrorString(err));
        return -2;
    }
    err = cudaMemcpy(pB_d, pB, byteCount, cudaMemcpyHostToDevice);
    if(err!=cudaSuccess)
    {
        fprintf(stderr, "Failed to copy B from host memory to device memory. error code=%d msg:%s\n",
                err, cudaGetErrorString(err));
        return -2;
    }

// Launch the Vector Add CUDA Kernel
    int threadsPerBlock = 256;
    int blocksPerGrid = (numElementCount + threadsPerBlock - 1) / threadsPerBlock;
    printf("CUDA kernel launch with %d blocks of %d threads\n", blocksPerGrid,
            threadsPerBlock);
    vectorAdd<<<blocksPerGrid, threadsPerBlock>>>(pA_d, pB_d, pC_d, numElementCount);
    err = cudaGetLastError();

    if (err != cudaSuccess) {
        fprintf(stderr, "Failed to launch vectorAdd kernel (error code %s)!\n",
                cudaGetErrorString(err));
        exit(EXIT_FAILURE);
    }
    else
    {
        printf("do vectorAdd success.err=%d\n", err);
    }
    // Copy the device result vector in device memory to the host result vector
    // in host memory.
    printf("Copy output data from the CUDA device to the host memory\n");
    err = cudaMemcpy(pC, pC_d, byteCount, cudaMemcpyDeviceToHost);

    if (err != cudaSuccess) {
        fprintf(stderr,
                "Failed to copy vector C from device to host (error code %s)!\n",
                cudaGetErrorString(err));
        exit(EXIT_FAILURE);
    }

    // Verify that the result vector is correct
    for (int i = 0; i < numElementCount; ++i) {
        if (fabs(pA[i] + pB[i] - pC[i]) > 1e-5) {
            fprintf(stderr, "Result verification failed at element %d!\n", i);
            exit(EXIT_FAILURE);
        }
    }
    printf("test success\n");

    cudaFree(pA_d);
    cudaFree(pB_d);
    cudaFree(pC_d);
    free(pA);
    free(pB);
    free(pC);
    return 0;
}
