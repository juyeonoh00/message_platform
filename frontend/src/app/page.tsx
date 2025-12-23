'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAppSelector } from '@/store/hooks'

export default function Home() {
  const router = useRouter()
  const { user, isInitialized } = useAppSelector((state) => state.auth)

  useEffect(() => {
    // Wait for auth to be initialized before redirecting
    if (!isInitialized) return

    if (user) {
      router.push('/workspaces')
    } else {
      router.push('/login')
    }
  }, [user, isInitialized, router])

  return (
    <div style={{ padding: '20px', textAlign: 'center' }}>
      <h1>Loading...</h1>
    </div>
  )
}
