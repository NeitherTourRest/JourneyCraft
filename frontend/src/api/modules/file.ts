import request from '@/api/request'

export const fileApi = {
  uploadImage: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<{ url: string; key: string; size: number }>('/api/file/upload/image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}
