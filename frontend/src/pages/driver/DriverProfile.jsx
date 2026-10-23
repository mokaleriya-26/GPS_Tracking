import React, { useState, useEffect } from 'react';
import { 
  User, Mail, Phone, ShieldCheck, Award, Calendar, 
  FileText, Truck, AlertCircle, RefreshCw 
} from 'lucide-react';
import { driverPortalApi } from '../../api/driverPortal';

export default function DriverProfile() {
  const [profile, setProfile] = useState(null);
  const [vehicles, setVehicles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchProfileData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [profileData, vehicleData] = await Promise.all([
        driverPortalApi.getProfile(),
        driverPortalApi.getVehicles()
      ]);
      setProfile(profileData);
      setVehicles(vehicleData);
    } catch (err) {
      console.error('Failed to load profile data:', err);
      setError('Unable to load your profile information. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProfileData();
  }, []);

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh]">
        <div className="w-12 h-12 border-4 border-emerald-500 border-t-transparent rounded-full animate-spin"></div>
        <p className="mt-4 text-slate-400 font-medium">Loading driver profile...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="max-w-4xl mx-auto p-6 bg-rose-500/10 border border-rose-500/20 rounded-2xl text-center">
        <AlertCircle className="w-10 h-10 text-rose-400 mx-auto mb-2" />
        <h3 className="text-lg font-semibold text-rose-300">Profile Error</h3>
        <p className="text-slate-400 text-sm mt-1">{error}</p>
        <button
          onClick={fetchProfileData}
          className="mt-4 inline-flex items-center gap-2 px-4 py-2 bg-slate-800 hover:bg-slate-700 text-white rounded-lg text-sm transition-colors"
        >
          <RefreshCw className="w-4 h-4" /> Retry
        </button>
      </div>
    );
  }

  const currentVehicles = vehicles.filter(v => v.assignmentStatus === 'CURRENT');

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-tight">My Profile</h1>
          <p className="text-sm text-slate-400 mt-1">
            Personal credentials, licensing status, and verified system assignment details
          </p>
        </div>
        <button
          onClick={fetchProfileData}
          className="inline-flex items-center gap-2 px-3 py-2 bg-slate-800/80 hover:bg-slate-700/80 border border-slate-700 text-slate-300 rounded-lg text-xs font-medium transition-colors"
        >
          <RefreshCw className="w-3.5 h-3.5" /> Refresh
        </button>
      </div>

      {/* Main Profile Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: Profile Card */}
        <div className="lg:col-span-1 bg-slate-800/60 backdrop-blur border border-slate-700/60 rounded-2xl p-6 flex flex-col items-center text-center">
          <div className="w-24 h-24 rounded-2xl bg-gradient-to-tr from-emerald-600 to-teal-500 flex items-center justify-center text-white shadow-xl shadow-emerald-500/10 ring-4 ring-emerald-500/20 mb-4">
            <User className="w-12 h-12" />
          </div>
          <h2 className="text-xl font-bold text-white">{profile?.name || 'Driver'}</h2>
          <span className="text-xs px-2.5 py-1 bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 rounded-full font-mono font-medium mt-1">
            {profile?.code || 'ID N/A'}
          </span>

          <div className="w-full mt-6 pt-6 border-t border-slate-700/50 space-y-3 text-left">
            <div className="flex items-center justify-between text-xs">
              <span className="text-slate-400">Account Status</span>
              <span className={`px-2 py-0.5 rounded text-[11px] font-semibold ${
                profile?.active !== false 
                  ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                  : 'bg-rose-500/10 text-rose-400 border border-rose-500/30'
              }`}>
                {profile?.active !== false ? 'ACTIVE' : 'INACTIVE'}
              </span>
            </div>

            <div className="flex items-center justify-between text-xs">
              <span className="text-slate-400">Driver License</span>
              <span className="font-mono text-slate-200">{profile?.licenseNumber || 'N/A'}</span>
            </div>

            <div className="flex items-center justify-between text-xs">
              <span className="text-slate-400">Enrolled Since</span>
              <span className="text-slate-200">
                {profile?.joinedOn ? new Date(profile.joinedOn).toLocaleDateString() : 'N/A'}
              </span>
            </div>
          </div>

          <div className="w-full mt-6 pt-6 border-t border-slate-700/50 bg-slate-900/40 rounded-xl p-4">
            <div className="flex items-center gap-3">
              <div className="p-2.5 bg-emerald-500/10 rounded-lg text-emerald-400">
                <ShieldCheck className="w-5 h-5" />
              </div>
              <div className="text-left">
                <div className="text-xs text-slate-400">Safety Index</div>
                <div className="text-lg font-bold text-white">
                  {profile?.safetyScore !== undefined && profile?.safetyScore !== null 
                    ? `${Number(profile.safetyScore).toFixed(1)} / 100` 
                    : '100 / 100'}
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Right Column: Detailed Cards */}
        <div className="lg:col-span-2 space-y-6">
          {/* Contact & Professional Details */}
          <div className="bg-slate-800/60 backdrop-blur border border-slate-700/60 rounded-2xl p-6">
            <h3 className="text-base font-semibold text-white mb-4 flex items-center gap-2">
              <FileText className="w-4 h-4 text-emerald-400" /> Professional Details
            </h3>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="bg-slate-900/40 p-4 rounded-xl border border-slate-700/40">
                <div className="flex items-center gap-2 text-xs text-slate-400 mb-1">
                  <Mail className="w-3.5 h-3.5 text-blue-400" /> Email Address
                </div>
                <div className="text-sm font-medium text-white truncate">
                  {profile?.email || 'No email registered'}
                </div>
              </div>

              <div className="bg-slate-900/40 p-4 rounded-xl border border-slate-700/40">
                <div className="flex items-center gap-2 text-xs text-slate-400 mb-1">
                  <Phone className="w-3.5 h-3.5 text-teal-400" /> Phone Contact
                </div>
                <div className="text-sm font-medium text-white font-mono">
                  {profile?.phone || 'No phone registered'}
                </div>
              </div>

              <div className="bg-slate-900/40 p-4 rounded-xl border border-slate-700/40">
                <div className="flex items-center gap-2 text-xs text-slate-400 mb-1">
                  <Award className="w-3.5 h-3.5 text-amber-400" /> Lifetime Recorded Trips
                </div>
                <div className="text-sm font-bold text-white">
                  {profile?.totalTrips || 0} Trips
                </div>
              </div>

              <div className="bg-slate-900/40 p-4 rounded-xl border border-slate-700/40">
                <div className="flex items-center gap-2 text-xs text-slate-400 mb-1">
                  <Calendar className="w-3.5 h-3.5 text-indigo-400" /> Record Created
                </div>
                <div className="text-sm font-medium text-white">
                  {profile?.createdAt ? new Date(profile.createdAt).toLocaleString() : 'System Seed'}
                </div>
              </div>
            </div>
          </div>

          {/* Currently Assigned Vehicle */}
          <div className="bg-slate-800/60 backdrop-blur border border-slate-700/60 rounded-2xl p-6">
            <h3 className="text-base font-semibold text-white mb-4 flex items-center gap-2">
              <Truck className="w-4 h-4 text-emerald-400" /> Active Vehicle Assignments
            </h3>

            {currentVehicles.length === 0 ? (
              <div className="p-6 bg-slate-900/40 rounded-xl border border-slate-700/40 text-center">
                <Truck className="w-8 h-8 text-slate-600 mx-auto mb-2" />
                <p className="text-slate-400 text-sm">No vehicles currently assigned directly in the database.</p>
                <p className="text-xs text-slate-500 mt-1">Contact your fleet administrator if you require assignment updates.</p>
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {currentVehicles.map(veh => (
                  <div key={veh.id} className="p-4 bg-slate-900/50 rounded-xl border border-emerald-500/20 relative overflow-hidden">
                    <div className="absolute top-0 right-0 w-16 h-16 bg-emerald-500/5 rounded-full blur-xl pointer-events-none"></div>
                    <div className="flex items-start justify-between">
                      <div>
                        <span className="text-[10px] px-2 py-0.5 bg-emerald-500/20 text-emerald-300 font-semibold rounded uppercase">
                          Current Primary
                        </span>
                        <h4 className="text-lg font-bold text-white mt-1">{veh.code}</h4>
                        <p className="text-xs text-slate-400">{veh.model || 'Standard Commercial'}</p>
                      </div>
                      <span className="text-xs font-mono font-bold bg-slate-800 px-2 py-1 rounded text-slate-300 border border-slate-700">
                        {veh.registrationNumber}
                      </span>
                    </div>

                    <div className="mt-4 pt-3 border-t border-slate-700/50 flex items-center justify-between text-xs text-slate-400">
                      <span>Status: <strong className="text-emerald-400">{veh.status || 'ACTIVE'}</strong></span>
                      <span>Trips with Driver: <strong className="text-white">{veh.tripCountWithDriver || 0}</strong></span>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
