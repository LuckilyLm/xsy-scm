import { create } from 'zustand'
import type { MallProfile } from '../types/mall'
import {
  getToken,
  getProfile,
  setToken,
  setProfile,
  clearSession,
} from '../services/storage'

interface SessionStore {
  token: string | null
  profile: MallProfile | null
  bootstrap: () => void
  login: (token: string, profile: MallProfile) => void
  refreshProfile: (profile: MallProfile) => void
  logout: () => void
}

export const useSession = create<SessionStore>((set) => ({
  token: getToken(),
  profile: getProfile(),
  bootstrap: () => set({ token: getToken(), profile: getProfile() }),
  login: (token, profile) => {
    setToken(token)
    setProfile(profile)
    set({ token, profile })
  },
  refreshProfile: (profile) => {
    setProfile(profile)
    set({ profile })
  },
  logout: () => {
    clearSession()
    set({ token: null, profile: null })
  },
}))
