import React, { useState, useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { 
  Users, Search, Shield, TrendingUp, Phone, Award, AlertTriangle, 
  RefreshCw, Plus, Edit3, Trash2, Eye, Truck, Mail, Calendar, 
  FileText, CheckCircle2, X, ChevronLeft, ChevronRight, LayoutGrid, List,
  IndianRupee, AlertCircle, ArrowUpDown, Filter, Gauge, Check, MapPin,
  Sparkles, Trophy
} from 'lucide-react';
import { Modal, Button, Form, Badge, Alert, Spinner } from 'react-bootstrap';
import { getDrivers, getDriverById, getDriverRanking, createDriver, updateDriver, deleteDriver } from '../api/drivers';

export default function Drivers() {
  const [searchParams] = useSearchParams();
  const [drivers, setDrivers] = useState([]);
  const [ranking, setRanking] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL'); // ALL, ACTIVE, INACTIVE
  const [vehicleFilter, setVehicleFilter] = useState('ALL'); // ALL, ASSIGNED, UNASSIGNED
  const [sortBy, setSortBy] = useState('ID_ASC'); // ID_ASC, ID_DESC, NAME_ASC, SCORE_DESC, TRIPS_DESC
  const [includeTests, setIncludeTests] = useState(false);
  const [error, setError] = useState(null);
  const [tab, setTab] = useState(() => searchParams.get('tab') === 'ranking' ? 'ranking' : 'list');
  const [viewMode, setViewMode] = useState('cards'); // 'cards' or 'table'

  // Pagination state
  const [currentPage, setCurrentPage] = useState(1);
  const pageSize = 12;

  // Modal states
  const [showAddModal, setShowAddModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [showViewModal, setShowViewModal] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [selectedDriver, setSelectedDriver] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [modalError, setModalError] = useState(null);

  // Form state for Add/Edit
  const [formData, setFormData] = useState({
    code: '',
    name: '',
    phone: '',
    email: '',
    licenseNumber: '',
    joinedOn: '',
    active: true
  });

  const loadDriversData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [drvs, rank] = await Promise.all([
        getDrivers({ includeTests }),
        getDriverRanking()
      ]);
      setDrivers(Array.isArray(drvs) ? drvs : (drvs?.content || []));
      setRanking(Array.isArray(rank) ? rank : []);
    } catch (err) {
      console.error('Failed to load driver data:', err);
      setError('Failed to fetch real driver records from PostgreSQL. Ensure the backend is active.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDriversData();
  }, [includeTests]);

  // Fleet KPI Summary Metrics
  const fleetStats = useMemo(() => {
    const total = drivers.length;
    const active = drivers.filter(d => d.active !== false).length;
    const assigned = drivers.filter(d => 
      (d.assignedVehicles && d.assignedVehicles.length > 0) || 
      (d.assignedVehicleSummary && d.assignedVehicleSummary !== 'None')
    ).length;
    const totalTrips = drivers.reduce((acc, d) => acc + (d.totalTrips || 0), 0);
    const totalAlerts = drivers.reduce((acc, d) => acc + (d.totalAlerts || 0), 0);
    const driversWithPenalties = drivers.filter(d => (d.totalPenalties || 0) > 0).length;
    const totalPenaltyAmount = drivers.reduce((acc, d) => acc + Number(d.totalPenaltyAmount || 0), 0);
    const avgScore = total > 0 
      ? (drivers.reduce((acc, d) => acc + (d.safetyScore != null ? d.safetyScore : 85), 0) / total).toFixed(1)
      : '0.0';

    return {
      total,
      active,
      inactive: total - active,
      assigned,
      unassigned: total - assigned,
      totalTrips,
      totalAlerts,
      driversWithPenalties,
      totalPenaltyAmount,
      avgScore: Number(avgScore)
    };
  }, [drivers]);

  // Filtering & Sorting
  const filteredDrivers = useMemo(() => {
    let result = drivers.filter(d => {
      // Status filter
      if (statusFilter === 'ACTIVE' && !d.active) return false;
      if (statusFilter === 'INACTIVE' && d.active) return false;

      // Vehicle assignment filter
      const isAssigned = (d.assignedVehicles && d.assignedVehicles.length > 0) || 
                         (d.assignedVehicleSummary && d.assignedVehicleSummary !== 'None');
      if (vehicleFilter === 'ASSIGNED' && !isAssigned) return false;
      if (vehicleFilter === 'UNASSIGNED' && isAssigned) return false;

      // Text search across all fields
      if (!search.trim()) return true;
      const q = search.toLowerCase();
      return (
        d.name?.toLowerCase().includes(q) ||
        d.code?.toLowerCase().includes(q) ||
        String(d.id).includes(q) ||
        d.phone?.toLowerCase().includes(q) ||
        d.email?.toLowerCase().includes(q) ||
        d.licenseNumber?.toLowerCase().includes(q) ||
        d.assignedVehicleSummary?.toLowerCase().includes(q)
      );
    });

    // Sorting
    return result.sort((a, b) => {
      if (sortBy === 'ID_ASC') return a.id - b.id;
      if (sortBy === 'ID_DESC') return b.id - a.id;
      if (sortBy === 'NAME_ASC') return (a.name || '').localeCompare(b.name || '');
      if (sortBy === 'SCORE_DESC') return (b.safetyScore || 0) - (a.safetyScore || 0);
      if (sortBy === 'TRIPS_DESC') return (b.totalTrips || 0) - (a.totalTrips || 0);
      return 0;
    });
  }, [drivers, search, statusFilter, vehicleFilter, sortBy]);

  // Pagination calculation
  const totalPages = Math.ceil(filteredDrivers.length / pageSize) || 1;
  const paginatedDrivers = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return filteredDrivers.slice(start, start + pageSize);
  }, [filteredDrivers, currentPage, pageSize]);

  // Reset page when filter changes
  useEffect(() => {
    setCurrentPage(1);
  }, [search, statusFilter, vehicleFilter, sortBy]);

  const scoreColor = (score) => {
    if (score == null) return '#94a3b8';
    if (score >= 80) return '#16a34a';
    if (score >= 60) return '#d97706';
    return '#dc2626';
  };

  const scoreBg = (score) => {
    if (score == null) return '#f1f5f9';
    if (score >= 80) return '#dcfce7';
    if (score >= 60) return '#fef9c3';
    return '#fee2e2';
  };

  const formatVehicleBadge = (v) => {
    if (!v) return '';
    if (typeof v === 'string') return v;
    if (v.vehicleCode && v.registrationNumber) return `${v.vehicleCode} (${v.registrationNumber})`;
    return v.vehicleCode || v.registrationNumber || `Vehicle #${v.id || ''}`;
  };

  // Actions
  const handleOpenAdd = () => {
    setFormData({
      code: `DRV${String(drivers.length + 1).padStart(3, '0')}`,
      name: '',
      phone: '',
      email: '',
      licenseNumber: '',
      joinedOn: new Date().toISOString().split('T')[0],
      active: true
    });
    setModalError(null);
    setShowAddModal(true);
  };

  const handleOpenEdit = (driver) => {
    setSelectedDriver(driver);
    setFormData({
      code: driver.code || '',
      name: driver.name || '',
      phone: driver.phone || '',
      email: driver.email || '',
      licenseNumber: driver.licenseNumber || '',
      joinedOn: driver.joinedOn || '',
      active: driver.active !== false
    });
    setModalError(null);
    setShowEditModal(true);
  };

  const handleOpenView = async (driver) => {
    setSelectedDriver(driver);
    setShowViewModal(true);
    try {
      const detailed = await getDriverById(driver.id);
      if (detailed) {
        setSelectedDriver(detailed);
      }
    } catch (err) {
      console.warn('Could not load detailed driver info, displaying card data:', err);
    }
  };

  const handleOpenDelete = (driver) => {
    setSelectedDriver(driver);
    setModalError(null);
    setShowDeleteModal(true);
  };

  const handleSubmitAdd = async (e) => {
    e.preventDefault();
    if (!formData.name.trim() || !formData.code.trim()) {
      setModalError('Driver name and driver code are required.');
      return;
    }
    try {
      setActionLoading(true);
      setModalError(null);
      await createDriver(formData);
      setShowAddModal(false);
      await loadDriversData();
    } catch (err) {
      setModalError(err?.response?.data?.message || err.message || 'Failed to create driver.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleSubmitEdit = async (e) => {
    e.preventDefault();
    if (!formData.name.trim()) {
      setModalError('Driver name is required.');
      return;
    }
    try {
      setActionLoading(true);
      setModalError(null);
      await updateDriver(selectedDriver.id, formData);
      setShowEditModal(false);
      await loadDriversData();
    } catch (err) {
      setModalError(err?.response?.data?.message || err.message || 'Failed to update driver.');
    } finally {
      setActionLoading(false);
    }
  };

  const handleConfirmDelete = async () => {
    try {
      setActionLoading(true);
      setModalError(null);
      await deleteDriver(selectedDriver.id);
      setShowDeleteModal(false);
      await loadDriversData();
    } catch (err) {
      setModalError(err?.response?.data?.message || err.message || 'Failed to delete driver.');
    } finally {
      setActionLoading(false);
    }
  };

  return (
    <div className="container-fluid py-4" style={{ fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif" }}>
      {/* Header */}
      <div className="d-flex flex-column flex-xl-row align-items-start align-items-xl-center justify-content-between gap-3 mb-4">
        <div style={{ minWidth: 0, flex: '1 1 auto' }}>
          <div className="d-flex align-items-center gap-2 mb-1 flex-wrap">
            <div 
              style={{ 
                width: 38, 
                height: 38, 
                borderRadius: 10, 
                background: 'linear-gradient(135deg, #2563eb, #1d4ed8)', 
                display: 'flex', 
                alignItems: 'center', 
                justifyContent: 'center', 
                boxShadow: '0 4px 10px rgba(37,99,235,0.25)', 
                color: '#fff',
                flexShrink: 0
              }}
            >
              <Users size={20} />
            </div>
            <h1 className="h3 fw-bold mb-0" style={{ color: '#0f172a', letterSpacing: '-0.02em' }}>
              Fleet Drivers Management
            </h1>
            <span className="badge bg-primary-subtle text-primary border border-primary-subtle rounded-pill px-2.5 py-1" style={{ fontSize: 11, fontWeight: 600 }}>
              Live PostgreSQL
            </span>
          </div>
          <p className="text-muted mb-0" style={{ fontSize: 13, maxWidth: 640 }}>
            Manage active driver profiles, real-time vehicle assignments, trip metrics, and driver safety scores.
          </p>
        </div>

        {/* Right Header Actions: Fleet Directory, Safety Ranking, Refresh, and Add Driver */}
        <div 
          className="d-flex align-items-center gap-2 flex-wrap flex-sm-nowrap ms-xl-auto align-self-start align-self-sm-end align-self-xl-center" 
          style={{ flexShrink: 0 }}
        >
          {/* View Tab Switcher */}
          <div className="bg-light p-1 rounded-3 border d-flex gap-1" style={{ borderColor: '#e2e8f0' }}>
            <button
              type="button"
              className={`btn btn-sm px-3 d-flex align-items-center gap-1.5 fw-semibold ${tab === 'list' ? 'bg-white shadow-sm text-primary' : 'text-muted border-0'}`}
              onClick={() => setTab('list')}
              style={{ borderRadius: 6, fontSize: 12, whiteSpace: 'nowrap' }}
            >
              <Users size={14} />
              <span>Fleet Directory ({drivers.length})</span>
            </button>
            <button
              type="button"
              className={`btn btn-sm px-3 d-flex align-items-center gap-1.5 fw-semibold ${tab === 'ranking' ? 'bg-white shadow-sm text-primary' : 'text-muted border-0'}`}
              onClick={() => setTab('ranking')}
              style={{ borderRadius: 6, fontSize: 12, whiteSpace: 'nowrap' }}
            >
              <Award size={14} />
              <span>Safety Ranking ({ranking.length})</span>
            </button>
          </div>

          <Button
            variant="outline-secondary"
            size="sm"
            onClick={loadDriversData}
            disabled={loading}
            className="d-flex align-items-center gap-1.5 bg-white border shadow-sm"
            style={{ borderRadius: 8, fontSize: 13, borderColor: '#e2e8f0', whiteSpace: 'nowrap' }}
            title="Refresh database records"
          >
            <RefreshCw size={14} className={loading ? 'spin' : ''} />
            <span className="d-none d-sm-inline">Refresh</span>
          </Button>

          <Button
            variant="primary"
            size="sm"
            onClick={handleOpenAdd}
            className="d-flex align-items-center gap-1.5 fw-semibold shadow-sm"
            style={{ 
              borderRadius: 8, 
              fontSize: 13, 
              background: 'linear-gradient(135deg, #2563eb, #1d4ed8)', 
              border: 'none', 
              padding: '6px 14px',
              whiteSpace: 'nowrap'
            }}
          >
            <Plus size={16} />
            <span>Add Driver</span>
          </Button>
        </div>
      </div>

      {error && (
        <Alert variant="danger" className="rounded-3 d-flex align-items-center justify-content-between p-3 mb-4 shadow-sm border-0">
          <div className="d-flex align-items-center gap-2">
            <AlertTriangle size={18} />
            <span>{error}</span>
          </div>
          <Button variant="outline-danger" size="sm" onClick={loadDriversData}>
            Retry
          </Button>
        </Alert>
      )}

      {/* Fleet KPI Metric Cards */}
      <div className="row g-3 mb-4">
        {/* Total Drivers */}
        <div className="col-12 col-sm-6 col-xl-3">
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14, background: '#ffffff', border: '1px solid #e2e8f0' }}>
            <div className="card-body p-3 d-flex align-items-center justify-content-between">
              <div>
                <span className="text-muted text-uppercase fw-semibold" style={{ fontSize: 11, letterSpacing: '0.04em' }}>
                  Total Registered Drivers
                </span>
                <div className="h3 fw-bold text-dark mb-0 mt-1">{fleetStats.total}</div>
                <div className="text-muted mt-1" style={{ fontSize: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span className="d-inline-block rounded-circle" style={{ width: 7, height: 7, background: '#10b981', flexShrink: 0 }}></span>
                  <span className="text-success fw-semibold">{fleetStats.active} Active</span>
                  <span className="text-muted" style={{ opacity: 0.6 }}>·</span>
                  <span className="text-secondary">{fleetStats.inactive} Inactive</span>
                </div>
              </div>
              <div 
                style={{ 
                  width: 48, 
                  height: 48, 
                  borderRadius: 12, 
                  background: '#eff6ff', 
                  color: '#2563eb', 
                  display: 'flex', 
                  alignItems: 'center', 
                  justifyContent: 'center',
                  flexShrink: 0 
                }}
              >
                <Users size={22} />
              </div>
            </div>
          </div>
        </div>

        {/* Assigned Vehicles */}
        <div className="col-12 col-sm-6 col-xl-3">
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14, background: '#ffffff', border: '1px solid #e2e8f0' }}>
            <div className="card-body p-3 d-flex align-items-center justify-content-between">
              <div>
                <span className="text-muted text-uppercase fw-semibold" style={{ fontSize: 11, letterSpacing: '0.04em' }}>
                  Vehicle Assignment
                </span>
                <div className="h3 fw-bold text-dark mb-0 mt-1">
                  {fleetStats.assigned} <span className="text-muted fw-normal" style={{ fontSize: 15 }}>/ {fleetStats.total}</span>
                </div>
                <div className="text-muted mt-1" style={{ fontSize: 12, display: 'flex', alignItems: 'center', gap: 8 }}>
                  <span className="badge bg-success-subtle text-success border border-success-subtle py-0.5 px-2" style={{ fontSize: 10, fontWeight: 600 }}>
                    {fleetStats.total > 0 ? Math.round((fleetStats.assigned / fleetStats.total) * 100) : 0}% Assigned
                  </span>
                  <span className="text-muted">{fleetStats.unassigned} unassigned</span>
                </div>
              </div>
              <div 
                style={{ 
                  width: 48, 
                  height: 48, 
                  borderRadius: 12, 
                  background: '#ecfdf5', 
                  color: '#059669', 
                  display: 'flex', 
                  alignItems: 'center', 
                  justifyContent: 'center',
                  flexShrink: 0 
                }}
              >
                <Truck size={22} />
              </div>
            </div>
          </div>
        </div>

        {/* Fleet Safety Score */}
        <div className="col-12 col-sm-6 col-xl-3">
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14, background: '#ffffff', border: '1px solid #e2e8f0' }}>
            <div className="card-body p-3 d-flex align-items-center justify-content-between">
              <div>
                <span className="text-muted text-uppercase fw-semibold" style={{ fontSize: 11, letterSpacing: '0.04em' }}>
                  Fleet Safety Index
                </span>
                <div className="h3 fw-bold mb-0 mt-1" style={{ color: scoreColor(fleetStats.avgScore) }}>
                  {fleetStats.avgScore} <span className="text-muted fw-normal" style={{ fontSize: 15 }}>/ 100</span>
                </div>
                <div className="text-muted mt-1" style={{ fontSize: 12 }}>
                  {fleetStats.avgScore >= 80 ? (
                    <span className="text-success fw-medium">Fleet in Good Standing</span>
                  ) : fleetStats.avgScore >= 60 ? (
                    <span className="text-warning fw-medium">Moderate Safety Rating</span>
                  ) : (
                    <span className="text-danger fw-medium">High Risk Infractions</span>
                  )}
                </div>
              </div>
              <div 
                style={{ 
                  width: 48, 
                  height: 48, 
                  borderRadius: 12, 
                  background: '#faf5ff', 
                  color: '#7c3aed', 
                  display: 'flex', 
                  alignItems: 'center', 
                  justifyContent: 'center',
                  flexShrink: 0 
                }}
              >
                <Shield size={22} />
              </div>
            </div>
          </div>
        </div>

        {/* Fleet Infractions & Penalties */}
        <div className="col-12 col-sm-6 col-xl-3">
          <div className="card border-0 shadow-sm h-100" style={{ borderRadius: 14, background: '#ffffff', border: '1px solid #e2e8f0' }}>
            <div className="card-body p-3 d-flex align-items-center justify-content-between">
              <div>
                <span className="text-muted text-uppercase fw-semibold" style={{ fontSize: 11, letterSpacing: '0.04em' }}>
                  Alerts & Penalties
                </span>
                <div className="h3 fw-bold text-dark mb-0 mt-1 d-flex align-items-baseline gap-2">
                  <span>{fleetStats.totalAlerts}</span>
                  <span className="text-danger fw-semibold" style={{ fontSize: 14 }}>
                    (₹{fleetStats.totalPenaltyAmount.toLocaleString('en-IN')})
                  </span>
                </div>
                <div className="text-muted mt-1" style={{ fontSize: 12 }}>
                  {fleetStats.driversWithPenalties} driver{fleetStats.driversWithPenalties !== 1 ? 's' : ''} with infractions
                </div>
              </div>
              <div 
                style={{ 
                  width: 48, 
                  height: 48, 
                  borderRadius: 12, 
                  background: '#fff1f2', 
                  color: '#e11d48', 
                  display: 'flex', 
                  alignItems: 'center', 
                  justifyContent: 'center',
                  flexShrink: 0 
                }}
              >
                <AlertTriangle size={22} />
              </div>
            </div>
          </div>
        </div>
      </div>

      {tab === 'list' && (
        <>
          {/* Controls: Search, Status Filter, Vehicle Filter, Sort, View Mode */}
          <div className="card border-0 shadow-sm mb-4" style={{ borderRadius: 14, border: '1px solid #e2e8f0' }}>
            <div className="card-body p-3">
              <div className="row g-2 align-items-center">
                {/* Search */}
                <div className="col-12 col-lg-4">
                  <div className="input-group">
                    <span className="input-group-text bg-white border-end-0" style={{ borderColor: '#cbd5e1' }}>
                      <Search size={15} className="text-muted" />
                    </span>
                    <input
                      type="text"
                      className="form-control border-start-0 ps-1"
                      style={{ borderColor: '#cbd5e1', fontSize: 13 }}
                      placeholder="Search name, code, phone, email, license, vehicle..."
                      value={search}
                      onChange={e => setSearch(e.target.value)}
                    />
                    {search && (
                      <button
                        className="btn btn-outline-secondary border-start-0 bg-white"
                        style={{ borderColor: '#cbd5e1' }}
                        onClick={() => setSearch('')}
                        title="Clear search"
                      >
                        <X size={14} />
                      </button>
                    )}
                  </div>
                </div>

                {/* Status Filter */}
                <div className="col-6 col-sm-4 col-lg-2">
                  <select
                    className="form-select form-select-sm"
                    style={{ borderRadius: 8, fontSize: 13, borderColor: '#cbd5e1', height: 38 }}
                    value={statusFilter}
                    onChange={e => setStatusFilter(e.target.value)}
                  >
                    <option value="ALL">All Statuses ({drivers.length})</option>
                    <option value="ACTIVE">Active Only</option>
                    <option value="INACTIVE">Inactive Only</option>
                  </select>
                </div>

                {/* Vehicle Filter */}
                <div className="col-6 col-sm-4 col-lg-2">
                  <select
                    className="form-select form-select-sm"
                    style={{ borderRadius: 8, fontSize: 13, borderColor: '#cbd5e1', height: 38 }}
                    value={vehicleFilter}
                    onChange={e => setVehicleFilter(e.target.value)}
                  >
                    <option value="ALL">All Vehicles</option>
                    <option value="ASSIGNED">Assigned ({fleetStats.assigned})</option>
                    <option value="UNASSIGNED">Unassigned ({fleetStats.unassigned})</option>
                  </select>
                </div>

                {/* Sort By */}
                <div className="col-6 col-sm-4 col-lg-2">
                  <select
                    className="form-select form-select-sm"
                    style={{ borderRadius: 8, fontSize: 13, borderColor: '#cbd5e1', height: 38 }}
                    value={sortBy}
                    onChange={e => setSortBy(e.target.value)}
                  >
                    <option value="ID_ASC">Sort: ID (Asc)</option>
                    <option value="ID_DESC">Sort: ID (Desc)</option>
                    <option value="NAME_ASC">Sort: Name (A-Z)</option>
                    <option value="SCORE_DESC">Sort: Safety Score</option>
                    <option value="TRIPS_DESC">Sort: Most Trips</option>
                  </select>
                </div>

                {/* Test Artifacts Toggle & View Switcher */}
                <div className="col-6 col-lg-2 d-flex align-items-center justify-content-end gap-2">
                  <div className="form-check form-switch mb-0 d-none d-xl-block" style={{ fontSize: 12 }}>
                    <input
                      className="form-check-input"
                      type="checkbox"
                      id="includeTestsCheck"
                      checked={includeTests}
                      onChange={e => setIncludeTests(e.target.checked)}
                      style={{ cursor: 'pointer' }}
                    />
                    <label className="form-check-label text-muted" htmlFor="includeTestsCheck" style={{ cursor: 'pointer', whiteSpace: 'nowrap' }}>
                      All DB Rows
                    </label>
                  </div>

                  <div className="btn-group btn-group-sm">
                    <button
                      className={`btn ${viewMode === 'cards' ? 'btn-primary' : 'btn-outline-secondary bg-white'}`}
                      onClick={() => setViewMode('cards')}
                      title="Cards View"
                      style={{ borderRadius: '6px 0 0 6px', padding: '6px 10px' }}
                    >
                      <LayoutGrid size={15} />
                    </button>
                    <button
                      className={`btn ${viewMode === 'table' ? 'btn-primary' : 'btn-outline-secondary bg-white'}`}
                      onClick={() => setViewMode('table')}
                      title="Table View"
                      style={{ borderRadius: '0 6px 6px 0', padding: '6px 10px' }}
                    >
                      <List size={15} />
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Results Summary */}
          <div className="d-flex align-items-center justify-content-between mb-3 px-1 text-muted" style={{ fontSize: 12 }}>
            <div className="d-flex align-items-center gap-2 flex-wrap">
              <span>
                Showing <strong className="text-dark">{filteredDrivers.length}</strong> matching drivers
                {includeTests ? ' (all database test records included)' : ' (production fleet)'}
              </span>
              {(search || statusFilter !== 'ALL' || vehicleFilter !== 'ALL') && (
                <button
                  type="button"
                  className="btn btn-link btn-sm p-0 text-decoration-none text-primary"
                  onClick={() => {
                    setSearch('');
                    setStatusFilter('ALL');
                    setVehicleFilter('ALL');
                  }}
                  style={{ fontSize: 12 }}
                >
                  Reset Filters
                </button>
              )}
            </div>
            {filteredDrivers.length > 0 && (
              <span>
                Page <strong className="text-dark">{currentPage}</strong> of <strong className="text-dark">{totalPages}</strong>
              </span>
            )}
          </div>

          {/* Loading */}
          {loading ? (
            <div className="py-5 text-center text-muted">
              <Spinner animation="border" variant="primary" className="mb-2" />
              <div style={{ fontSize: 13 }}>Loading real driver records from PostgreSQL…</div>
            </div>
          ) : filteredDrivers.length === 0 ? (
            /* Empty state */
            <div className="card border-0 shadow-sm p-5 text-center" style={{ borderRadius: 14 }}>
              <Users size={44} className="text-muted mx-auto mb-2 opacity-50" />
              <h5 className="fw-bold text-dark mt-2">No Driver Records Found</h5>
              <p className="text-muted small mb-3">
                {search ? `No drivers match your search query "${search}".` : 'No driver records exist in the database matching the criteria.'}
              </p>
              {(search || statusFilter !== 'ALL' || vehicleFilter !== 'ALL') && (
                <Button 
                  variant="outline-primary" 
                  size="sm" 
                  onClick={() => { setSearch(''); setStatusFilter('ALL'); setVehicleFilter('ALL'); }} 
                  className="mx-auto" 
                  style={{ width: 'fit-content' }}
                >
                  Clear Filters
                </Button>
              )}
            </div>
          ) : viewMode === 'cards' ? (
            /* Cards View */
            <div className="row g-4">
              {paginatedDrivers.map(driver => (
                <div key={driver.id} className="col-12 col-md-6 col-xl-4">
                  <div
                    className="card border-0 shadow-sm h-100"
                    style={{
                      borderRadius: 16,
                      transition: 'transform 0.2s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.2s cubic-bezier(0.16, 1, 0.3, 1), border-color 0.2s ease',
                      border: '1px solid #e2e8f0',
                      background: '#ffffff',
                      boxShadow: '0 2px 6px -1px rgba(15, 23, 42, 0.05), 0 1px 4px -1px rgba(15, 23, 42, 0.03)'
                    }}
                    onMouseEnter={e => {
                      e.currentTarget.style.transform = 'translateY(-3px)';
                      e.currentTarget.style.boxShadow = '0 16px 24px -4px rgba(15, 23, 42, 0.08), 0 6px 10px -2px rgba(15, 23, 42, 0.03)';
                      e.currentTarget.style.borderColor = '#cbd5e1';
                    }}
                    onMouseLeave={e => {
                      e.currentTarget.style.transform = 'none';
                      e.currentTarget.style.boxShadow = '0 2px 6px -1px rgba(15, 23, 42, 0.05), 0 1px 4px -1px rgba(15, 23, 42, 0.03)';
                      e.currentTarget.style.borderColor = '#e2e8f0';
                    }}
                  >
                    <div className="card-body p-4 d-flex flex-column justify-content-between">
                      <div>
                        {/* Header Section */}
                        <div className="d-flex align-items-center justify-content-between gap-3 mb-3">
                          <div 
                            style={{ 
                              minWidth: 0, 
                              flex: 1, 
                              display: 'flex', 
                              alignItems: 'center', 
                              gap: 15 
                            }}
                          >
                            {/* Circular Avatar with Active Status Dot */}
                            <div className="position-relative flex-shrink-0">
                              <div
                                style={{
                                  width: 46,
                                  height: 46,
                                  borderRadius: '50%',
                                  background: driver.active !== false 
                                    ? 'linear-gradient(135deg, #2563eb, #1d4ed8)' 
                                    : 'linear-gradient(135deg, #94a3b8, #64748b)',
                                  color: '#ffffff',
                                  display: 'flex',
                                  alignItems: 'center',
                                  justifyContent: 'center',
                                  fontWeight: 700,
                                  fontSize: 17,
                                  textTransform: 'uppercase',
                                  boxShadow: driver.active !== false 
                                    ? '0 4px 10px rgba(37, 99, 235, 0.25)' 
                                    : '0 2px 6px rgba(148, 163, 184, 0.2)'
                                }}
                              >
                                {driver.name ? driver.name.charAt(0).toUpperCase() : '?'}
                              </div>
                              <span 
                                style={{ 
                                  position: 'absolute', 
                                  bottom: 1, 
                                  right: 1, 
                                  width: 11, 
                                  height: 11, 
                                  borderRadius: '50%', 
                                  background: driver.active !== false ? '#10b981' : '#cbd5e1', 
                                  border: '2px solid #ffffff' 
                                }}
                                title={driver.active !== false ? 'Active Driver' : 'Inactive Driver'}
                              />
                            </div>

                            {/* Driver Name & Code - Vertically centered beside avatar with 15px gap */}
                            <div style={{ minWidth: 0, flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
                              <div className="fw-bold text-dark text-truncate" style={{ fontSize: 15.5, lineHeight: 1.25 }} title={driver.name}>
                                {driver.name}
                              </div>
                              <div className="d-flex align-items-center gap-1.5 mt-1">
                                <span className="badge bg-light text-secondary border font-monospace px-1.5 py-0.5" style={{ fontSize: 11, fontWeight: 600 }}>
                                  {driver.code}
                                </span>
                                <span className="text-muted" style={{ fontSize: 11 }}>
                                  #{driver.id}
                                </span>
                              </div>
                            </div>
                          </div>

                          {/* Status Pill & Safety Score Aligned Right */}
                          <div className="d-flex flex-column align-items-end gap-1 flex-shrink-0 ms-2">
                            <span
                              className="badge"
                              style={{
                                backgroundColor: driver.active !== false ? '#ecfdf5' : '#f8fafc',
                                color: driver.active !== false ? '#059669' : '#64748b',
                                border: `1px solid ${driver.active !== false ? '#a7f3d0' : '#e2e8f0'}`,
                                fontSize: 10,
                                fontWeight: 700,
                                padding: '2px 7px',
                                borderRadius: 5
                              }}
                            >
                              {driver.active !== false ? 'ACTIVE' : 'INACTIVE'}
                            </span>
                            {driver.safetyScore != null && (
                              <div
                                style={{
                                  background: scoreBg(driver.safetyScore),
                                  color: scoreColor(driver.safetyScore),
                                  borderRadius: 6,
                                  padding: '2px 7px',
                                  fontSize: 11.5,
                                  fontWeight: 700,
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: 3.5,
                                  border: `1px solid ${scoreColor(driver.safetyScore)}33`
                                }}
                                title="Safety Score"
                              >
                                <Shield size={11} />
                                <span>{typeof driver.safetyScore === 'number' ? driver.safetyScore.toFixed(1) : driver.safetyScore}</span>
                              </div>
                            )}
                          </div>
                        </div>

                        {/* Contact Information Section - Clean, spacious rows on white background */}
                        <div className="pt-2 pb-1">
                          {/* Email Row */}
                          <div className="d-flex justify-content-between align-items-center mb-2.5">
                            <span className="text-muted d-flex align-items-center gap-2" style={{ fontSize: 12.5, minWidth: 70 }}>
                              <Mail size={13} className="text-muted flex-shrink-0" />
                              <span>Email</span>
                            </span>
                            <span className="text-dark fw-medium text-truncate ms-2" style={{ maxWidth: 200, fontSize: 12.5 }} title={driver.email || 'N/A'}>
                              {driver.email ? (
                                <a href={`mailto:${driver.email}`} className="text-decoration-none text-dark hover-primary">
                                  {driver.email}
                                </a>
                              ) : (
                                <span className="text-muted">N/A</span>
                              )}
                            </span>
                          </div>

                          {/* Phone Row */}
                          <div className="d-flex justify-content-between align-items-center mb-2.5">
                            <span className="text-muted d-flex align-items-center gap-2" style={{ fontSize: 12.5, minWidth: 70 }}>
                              <Phone size={13} className="text-muted flex-shrink-0" />
                              <span>Phone</span>
                            </span>
                            <span className="text-dark font-monospace fw-medium ms-2" style={{ fontSize: 12.5 }}>
                              {driver.phone ? (
                                <a href={`tel:${driver.phone}`} className="text-decoration-none text-dark">
                                  {driver.phone}
                                </a>
                              ) : (
                                <span className="text-muted">N/A</span>
                              )}
                            </span>
                          </div>

                          {/* License Row */}
                          <div className="d-flex justify-content-between align-items-center mb-2.5">
                            <span className="text-muted d-flex align-items-center gap-2" style={{ fontSize: 12.5, minWidth: 70 }}>
                              <FileText size={13} className="text-muted flex-shrink-0" />
                              <span>License</span>
                            </span>
                            <span className="text-dark font-monospace fw-medium ms-2" style={{ fontSize: 12.5 }}>
                              {driver.licenseNumber || <span className="text-muted">N/A</span>}
                            </span>
                          </div>
                        </div>

                        {/* Dedicated Assigned Vehicles Section */}
                        <div className="pt-2.5 mt-1 border-top" style={{ borderColor: '#f1f5f9' }}>
                          <div className="text-muted mb-2" style={{ fontSize: 12, fontWeight: 500, display: 'flex', alignItems: 'center', gap: 6 }}>
                            <Truck size={13} className="text-muted" />
                            <span>Assigned Vehicles</span>
                          </div>
                          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px 10px' }}>
                            {(() => {
                              const vList = (driver.assignedVehicles && driver.assignedVehicles.length > 0)
                                ? driver.assignedVehicles
                                : (driver.assignedVehicleSummary && driver.assignedVehicleSummary !== 'None')
                                  ? driver.assignedVehicleSummary.split(',').map(s => s.trim()).filter(Boolean)
                                  : [];
                              if (vList.length === 0) {
                                return <span className="text-muted" style={{ fontSize: 12, fontStyle: 'italic' }}>Unassigned</span>;
                              }
                              return vList.map((v, idx) => (
                                <span 
                                  key={idx} 
                                  className="badge rounded-pill fw-medium"
                                  style={{ 
                                    fontSize: 11.5, 
                                    padding: '5px 11px', 
                                    background: '#ecfdf5', 
                                    color: '#065f46', 
                                    border: '1px solid #a7f3d0',
                                    display: 'inline-flex',
                                    alignItems: 'center',
                                    gap: 6
                                  }}
                                  title={typeof v === 'object' && v.model ? `${v.vehicleCode} - ${v.model}` : (typeof v === 'string' ? v : '')}
                                >
                                  <Truck size={11} className="text-success" />
                                  <span>{formatVehicleBadge(v)}</span>
                                </span>
                              ));
                            })()}
                          </div>
                        </div>

                        {/* Redesigned Statistics Section - Clean, open, equal-width row */}
                        <div className="py-2.5 my-3 border-top border-bottom" style={{ borderColor: '#f1f5f9' }}>
                          <div className="row g-0 text-center">
                            <div className="col-3">
                              <div className="text-muted text-uppercase mb-1" style={{ fontSize: 9.5, letterSpacing: '0.03em', fontWeight: 600, whiteSpace: 'nowrap' }}>Trips</div>
                              <div className="fw-bold text-dark" style={{ fontSize: 14 }}>{driver.totalTrips || 0}</div>
                            </div>
                            <div className="col-3 border-start" style={{ borderColor: '#f1f5f9' }}>
                              <div className="text-muted text-uppercase mb-1" style={{ fontSize: 9.5, letterSpacing: '0.03em', fontWeight: 600, whiteSpace: 'nowrap' }}>Distance</div>
                              <div className="fw-bold text-dark" style={{ fontSize: 14 }}>
                                {driver.totalDistanceKm ? `${Number(driver.totalDistanceKm).toFixed(0)}k` : '0k'}
                              </div>
                            </div>
                            <div className="col-3 border-start" style={{ borderColor: '#f1f5f9' }}>
                              <div className="text-muted text-uppercase mb-1" style={{ fontSize: 9.5, letterSpacing: '0.03em', fontWeight: 600, whiteSpace: 'nowrap' }}>Alerts</div>
                              <div className="fw-bold text-dark" style={{ fontSize: 14 }}>{driver.totalAlerts || 0}</div>
                            </div>
                            <div className="col-3 border-start" style={{ borderColor: '#f1f5f9' }}>
                              <div className="text-muted text-uppercase mb-1" style={{ fontSize: 9.5, letterSpacing: '0.03em', fontWeight: 600, whiteSpace: 'nowrap' }}>Penalties</div>
                              <div className="fw-bold" style={{ fontSize: 14, color: (driver.totalPenalties || 0) > 0 ? '#e11d48' : '#0f172a' }}>
                                {driver.totalPenalties || 0}
                              </div>
                            </div>
                          </div>
                        </div>
                      </div>

                      {/* Card Action Buttons - Dedicated footer */}
                      <div className="d-flex align-items-center justify-content-between pt-1">
                        <Button
                          variant="primary"
                          size="sm"
                          onClick={() => handleOpenView(driver)}
                          className="d-flex align-items-center gap-1.5 fw-semibold shadow-sm"
                          style={{ 
                            fontSize: 12.5, 
                            borderRadius: 8, 
                            padding: '7px 14px',
                            background: 'linear-gradient(135deg, #2563eb, #1d4ed8)',
                            border: 'none'
                          }}
                          title="View Full Driver Details"
                        >
                          <Eye size={14} />
                          <span>View Details</span>
                        </Button>

                        <div className="d-flex align-items-center gap-2">
                          <Button
                            variant="light"
                            size="sm"
                            onClick={() => handleOpenEdit(driver)}
                            className="d-flex align-items-center gap-1.5 border bg-white text-dark"
                            style={{ fontSize: 12.5, borderRadius: 8, padding: '6px 12px', borderColor: '#e2e8f0' }}
                            title="Edit Driver Record"
                          >
                            <Edit3 size={13} className="text-secondary" />
                            <span>Edit</span>
                          </Button>

                          <Button
                            variant="outline-danger"
                            size="sm"
                            onClick={() => handleOpenDelete(driver)}
                            className="d-flex align-items-center justify-content-center"
                            style={{ borderRadius: 8, width: 34, height: 34, padding: 0 }}
                            title="Delete Driver"
                          >
                            <Trash2 size={14} />
                          </Button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            /* Table View */
            <div className="card border-0 shadow-sm" style={{ borderRadius: 14, border: '1px solid #e2e8f0' }}>
              <div className="table-responsive" style={{ borderRadius: 14 }}>
                <table className="table table-hover align-middle mb-0" style={{ minWidth: 980, fontSize: 13 }}>
                  <thead style={{ background: '#f8fafc', borderBottom: '1px solid #e2e8f0' }}>
                    <tr style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.04em', color: '#64748b' }}>
                      <th className="ps-3" style={{ width: 50 }}>ID</th>
                      <th style={{ minWidth: 160 }}>Driver Name & Code</th>
                      <th style={{ minWidth: 170 }}>Assigned Vehicles</th>
                      <th style={{ minWidth: 130 }}>Contact Info</th>
                      <th style={{ minWidth: 110 }}>License</th>
                      <th style={{ width: 90 }}>Safety Score</th>
                      <th style={{ width: 95 }}>Trips / Dist</th>
                      <th style={{ width: 105 }}>Infractions</th>
                      <th style={{ width: 85 }}>Status</th>
                      <th className="pe-3 text-end" style={{ width: 115, minWidth: 115, whiteSpace: 'nowrap' }}>Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    {paginatedDrivers.map(driver => (
                      <tr key={driver.id}>
                        <td className="ps-3 font-monospace fw-semibold text-muted">
                          #{driver.id}
                        </td>
                        <td>
                          <div className="d-flex align-items-center gap-2">
                            <div
                              style={{
                                width: 32,
                                height: 32,
                                borderRadius: '50%',
                                background: driver.active !== false ? 'linear-gradient(135deg, #2563eb, #1d4ed8)' : '#94a3b8',
                                color: '#fff',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                fontWeight: 700,
                                fontSize: 13,
                                flexShrink: 0
                              }}
                            >
                              {driver.name ? driver.name.charAt(0).toUpperCase() : '?'}
                            </div>
                            <div>
                              <div className="fw-bold text-dark">{driver.name}</div>
                              <span className="badge bg-light text-secondary border font-monospace" style={{ fontSize: 10 }}>
                                {driver.code}
                              </span>
                            </div>
                          </div>
                        </td>
                        <td>
                          {driver.assignedVehicles && driver.assignedVehicles.length > 0 ? (
                            <div className="d-flex flex-wrap gap-1">
                              {driver.assignedVehicles.map((v, idx) => (
                                <span key={idx} className="badge bg-success-subtle text-success border border-success-subtle fw-medium" style={{ fontSize: 11 }}>
                                  <Truck size={10} className="me-1 inline" />
                                  {formatVehicleBadge(v)}
                                </span>
                              ))}
                            </div>
                          ) : driver.assignedVehicleSummary && driver.assignedVehicleSummary !== 'None' ? (
                            <div className="d-flex flex-wrap gap-1">
                              {driver.assignedVehicleSummary.split(',').map((part, idx) => (
                                <span key={idx} className="badge bg-success-subtle text-success border border-success-subtle fw-medium" style={{ fontSize: 11 }}>
                                  <Truck size={10} className="me-1 inline" />
                                  {part.trim()}
                                </span>
                              ))}
                            </div>
                          ) : (
                            <span className="text-muted small">Unassigned</span>
                          )}
                        </td>
                        <td>
                          <div className="d-flex flex-column gap-0.5">
                            <span className="font-monospace text-dark" style={{ fontSize: 12 }}>
                              {driver.phone || <span className="text-muted">—</span>}
                            </span>
                            <span className="text-muted text-truncate" style={{ fontSize: 11, maxWidth: 140 }} title={driver.email || ''}>
                              {driver.email || '—'}
                            </span>
                          </div>
                        </td>
                        <td className="font-monospace" style={{ fontSize: 12 }}>
                          {driver.licenseNumber || <span className="text-muted">—</span>}
                        </td>
                        <td>
                          {driver.safetyScore != null ? (
                            <span
                              className="badge font-monospace"
                              style={{
                                background: scoreBg(driver.safetyScore),
                                color: scoreColor(driver.safetyScore),
                                fontSize: 11,
                                fontWeight: 700,
                                border: `1px solid ${scoreColor(driver.safetyScore)}33`
                              }}
                            >
                              {typeof driver.safetyScore === 'number' ? driver.safetyScore.toFixed(1) : driver.safetyScore}
                            </span>
                          ) : (
                            <span className="text-muted small">—</span>
                          )}
                        </td>
                        <td>
                          <div style={{ fontSize: 12 }}>
                            <strong className="text-dark">{driver.totalTrips || 0}</strong> trips
                          </div>
                          <div className="text-muted" style={{ fontSize: 11 }}>
                            {driver.totalDistanceKm ? `${Number(driver.totalDistanceKm).toFixed(0)} km` : '0 km'}
                          </div>
                        </td>
                        <td>
                          {Number(driver.totalPenalties || 0) === 0 ? (
                            <span className="text-muted small">{driver.totalAlerts || 0} alerts</span>
                          ) : Number(driver.totalPenaltyAmount || 0) === 0 ? (
                            <span className="badge bg-warning-subtle text-warning-emphasis border border-warning-subtle" style={{ fontSize: 10 }}>
                              {driver.totalPenalties} Warning
                            </span>
                          ) : (
                            <span className="badge bg-danger-subtle text-danger border border-danger-subtle" style={{ fontSize: 10 }}>
                              {driver.totalPenalties} (₹{Number(driver.totalPenaltyAmount).toFixed(0)})
                            </span>
                          )}
                        </td>
                        <td>
                          <span
                            className="badge"
                            style={{
                              backgroundColor: driver.active !== false ? '#ecfdf5' : '#f1f5f9',
                              color: driver.active !== false ? '#059669' : '#64748b',
                              border: `1px solid ${driver.active !== false ? '#a7f3d0' : '#e2e8f0'}`,
                              fontSize: 10,
                              fontWeight: 700
                            }}
                          >
                            {driver.active !== false ? 'ACTIVE' : 'INACTIVE'}
                          </span>
                        </td>
                        <td className="pe-3 text-end" style={{ width: 115, minWidth: 115, whiteSpace: 'nowrap' }}>
                          <div className="d-flex align-items-center justify-content-end gap-1">
                            <Button
                              variant="light"
                              size="sm"
                              onClick={() => handleOpenView(driver)}
                              className="p-1.5 border bg-white text-primary"
                              style={{ borderRadius: 6 }}
                              title="View Details"
                            >
                              <Eye size={14} />
                            </Button>
                            <Button
                              variant="light"
                              size="sm"
                              onClick={() => handleOpenEdit(driver)}
                              className="p-1.5 border bg-white text-secondary"
                              style={{ borderRadius: 6 }}
                              title="Edit Driver"
                            >
                              <Edit3 size={14} />
                            </Button>
                            <Button
                              variant="outline-danger"
                              size="sm"
                              onClick={() => handleOpenDelete(driver)}
                              className="p-1.5"
                              style={{ borderRadius: 6 }}
                              title="Delete Driver"
                            >
                              <Trash2 size={14} />
                            </Button>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* Pagination Controls */}
          {totalPages > 1 && (
            <div className="d-flex align-items-center justify-content-between mt-4 px-2">
              <span className="text-muted" style={{ fontSize: 13 }}>
                Page <strong className="text-dark">{currentPage}</strong> of <strong className="text-dark">{totalPages}</strong>
              </span>

              <div className="d-flex align-items-center gap-1">
                <Button
                  variant="light"
                  size="sm"
                  onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                  disabled={currentPage === 1}
                  className="border bg-white"
                  style={{ borderRadius: 6 }}
                >
                  <ChevronLeft size={16} />
                </Button>

                {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                  let pNum = i + 1;
                  if (totalPages > 5 && currentPage > 3) {
                    pNum = Math.min(totalPages - 4 + i, currentPage - 2 + i);
                  }
                  return (
                    <Button
                      key={pNum}
                      variant={currentPage === pNum ? 'primary' : 'light'}
                      size="sm"
                      onClick={() => setCurrentPage(pNum)}
                      className={currentPage === pNum ? '' : 'border bg-white'}
                      style={{ borderRadius: 6, minWidth: 32, fontSize: 12, fontWeight: currentPage === pNum ? 700 : 400 }}
                    >
                      {pNum}
                    </Button>
                  );
                })}

                <Button
                  variant="light"
                  size="sm"
                  onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                  disabled={currentPage === totalPages}
                  className="border bg-white"
                  style={{ borderRadius: 6 }}
                >
                  <ChevronRight size={16} />
                </Button>
              </div>
            </div>
          )}
        </>
      )}

      {/* Safety Ranking Tab */}
      {tab === 'ranking' && (
        <>
          <div className="card border-0 shadow-sm p-3 mb-4 rounded-3" style={{ background: '#f0fdf4', border: '1px solid #bbf7d0' }}>
            <div className="d-flex align-items-center gap-2" style={{ fontSize: 13, color: '#166534' }}>
              <Shield size={18} className="text-success flex-shrink-0" />
              <span>
                Safety scores are calculated dynamically from telemetry events: 
                <strong> 100 - (Overspeed×5) - (Harsh Braking×4) - (Harsh Acceleration×4) - (Night Driving hrs×3) - (Fatigue×8) + (Clean Trips×1)</strong>.
              </span>
            </div>
          </div>

          {/* Top 3 Podium Cards */}
          {ranking.length >= 3 && (
            <div className="row g-3 mb-4">
              {/* #2 Silver */}
              <div className="col-12 col-md-4 order-2 order-md-1">
                <div className="card border-0 shadow-sm h-100 text-center p-3" style={{ borderRadius: 14, border: '1px solid #e2e8f0', background: '#f8fafc' }}>
                  <div className="badge bg-secondary-subtle text-secondary mx-auto mb-2 px-3 py-1 rounded-pill" style={{ fontSize: 12, fontWeight: 700 }}>
                    🥈 RANK #2 · SILVER
                  </div>
                  <h5 className="fw-bold text-dark mb-0">{ranking[1]?.driverName}</h5>
                  <div className="text-muted small mb-2 font-monospace">{ranking[1]?.driverCode}</div>
                  <div className="h3 fw-bold mb-1" style={{ color: scoreColor(ranking[1]?.safetyScore) }}>
                    {ranking[1]?.safetyScore?.toFixed ? ranking[1]?.safetyScore.toFixed(1) : ranking[1]?.safetyScore}
                  </div>
                  <div className="text-muted small">
                    {ranking[1]?.tripCount || 0} trips · {ranking[1]?.totalDistanceKm?.toFixed ? ranking[1]?.totalDistanceKm.toFixed(0) : 0} km
                  </div>
                </div>
              </div>

              {/* #1 Gold Champion */}
              <div className="col-12 col-md-4 order-1 order-md-2">
                <div 
                  className="card border-0 shadow-sm h-100 text-center p-3" 
                  style={{ 
                    borderRadius: 14, 
                    border: '2px solid #f59e0b', 
                    background: 'linear-gradient(180deg, #fffbeb 0%, #ffffff 100%)',
                    transform: 'translateY(-4px)'
                  }}
                >
                  <div className="badge bg-warning text-dark mx-auto mb-2 px-3 py-1 rounded-pill" style={{ fontSize: 12, fontWeight: 700 }}>
                    👑 TOP PERFORMER · RANK #1 GOLD
                  </div>
                  <h4 className="fw-bold text-dark mb-0">{ranking[0]?.driverName}</h4>
                  <div className="text-muted small mb-2 font-monospace">{ranking[0]?.driverCode}</div>
                  <div className="h2 fw-bold text-warning mb-1">
                    {ranking[0]?.safetyScore?.toFixed ? ranking[0]?.safetyScore.toFixed(1) : ranking[0]?.safetyScore}
                  </div>
                  <div className="text-muted small">
                    {ranking[0]?.tripCount || 0} trips · {ranking[0]?.totalDistanceKm?.toFixed ? ranking[0]?.totalDistanceKm.toFixed(0) : 0} km
                  </div>
                </div>
              </div>

              {/* #3 Bronze */}
              <div className="col-12 col-md-4 order-3 order-md-3">
                <div className="card border-0 shadow-sm h-100 text-center p-3" style={{ borderRadius: 14, border: '1px solid #e2e8f0', background: '#f8fafc' }}>
                  <div className="badge bg-light text-dark border mx-auto mb-2 px-3 py-1 rounded-pill" style={{ fontSize: 12, fontWeight: 700 }}>
                    🥉 RANK #3 · BRONZE
                  </div>
                  <h5 className="fw-bold text-dark mb-0">{ranking[2]?.driverName}</h5>
                  <div className="text-muted small mb-2 font-monospace">{ranking[2]?.driverCode}</div>
                  <div className="h3 fw-bold mb-1" style={{ color: scoreColor(ranking[2]?.safetyScore) }}>
                    {ranking[2]?.safetyScore?.toFixed ? ranking[2]?.safetyScore.toFixed(1) : ranking[2]?.safetyScore}
                  </div>
                  <div className="text-muted small">
                    {ranking[2]?.tripCount || 0} trips · {ranking[2]?.totalDistanceKm?.toFixed ? ranking[2]?.totalDistanceKm.toFixed(0) : 0} km
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Full Safety Ranking Table */}
          <div className="card border-0 shadow-sm" style={{ borderRadius: 14, border: '1px solid #e2e8f0' }}>
            <div className="table-responsive" style={{ borderRadius: 14 }}>
              <table className="table table-hover align-middle mb-0" style={{ minWidth: 980, fontSize: 13 }}>
                <thead style={{ background: '#f8fafc', borderBottom: '1px solid #e2e8f0' }}>
                  <tr style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.04em', color: '#64748b' }}>
                    <th style={{ width: 80 }} className="ps-4">Rank</th>
                    <th style={{ minWidth: 180 }}>Driver</th>
                    <th style={{ minWidth: 150 }}>Safety Score</th>
                    <th style={{ width: 90 }}>Trips</th>
                    <th style={{ minWidth: 120 }}>Distance</th>
                    <th style={{ width: 110 }}>Overspeed</th>
                    <th style={{ width: 120 }}>Harsh Braking</th>
                    <th style={{ width: 90 }} className="pe-4 text-end">Alerts</th>
                  </tr>
                </thead>
                <tbody>
                  {ranking.map((r, i) => (
                    <tr key={r.driverId}>
                      <td style={{ width: 80 }} className="ps-4">
                        <span style={{
                          fontWeight: 700, fontSize: 14,
                          color: i === 0 ? '#d97706' : i === 1 ? '#64748b' : i === 2 ? '#b45309' : '#94a3b8'
                        }}>
                          #{r.rank || i + 1}
                        </span>
                      </td>
                      <td style={{ minWidth: 180 }}>
                        <div className="fw-semibold text-dark">{r.driverName}</div>
                        <div className="text-muted font-monospace" style={{ fontSize: 11 }}>{r.driverCode}</div>
                      </td>
                      <td style={{ minWidth: 150 }}>
                        <div className="d-flex align-items-center gap-2">
                          <div style={{ flex: 1, height: 6, background: '#f1f5f9', borderRadius: 3, maxWidth: 80 }}>
                            <div style={{
                              width: `${Math.min(100, Math.max(0, r.safetyScore || 0))}%`, height: '100%', borderRadius: 3,
                              background: scoreColor(r.safetyScore)
                            }} />
                          </div>
                          <span style={{ color: scoreColor(r.safetyScore), fontWeight: 700, fontSize: 13 }}>
                            {r.safetyScore?.toFixed ? r.safetyScore.toFixed(1) : r.safetyScore || 0}
                          </span>
                        </div>
                      </td>
                      <td>{r.tripCount || 0}</td>
                      <td>{r.totalDistanceKm?.toFixed ? r.totalDistanceKm.toFixed(0) : r.totalDistanceKm || 0} km</td>
                      <td>
                        <span className={`badge ${r.overspeedEvents > 0 ? 'bg-danger-subtle text-danger border border-danger-subtle' : 'bg-success-subtle text-success border border-success-subtle'}`}>
                          {r.overspeedEvents || 0}
                        </span>
                      </td>
                      <td>
                        <span className={`badge ${r.harshBrakingEvents > 0 ? 'bg-warning-subtle text-warning-emphasis border border-warning-subtle' : 'bg-success-subtle text-success border border-success-subtle'}`}>
                          {r.harshBrakingEvents || 0}
                        </span>
                      </td>
                      <td className="pe-4 text-end font-semibold">{r.totalAlerts || 0}</td>
                    </tr>
                  ))}
                  {ranking.length === 0 && (
                    <tr>
                      <td colSpan={8} className="text-center py-4 text-muted">
                        No ranking data available. Ensure driver_daily_stats table has data.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}

      {/* VIEW DRIVER DETAILS MODAL */}
      <Modal 
        show={showViewModal} 
        onHide={() => setShowViewModal(false)} 
        size="lg" 
        centered 
        scrollable
        backdrop={true}
        keyboard={true}
      >
        <Modal.Header closeButton className="border-bottom py-3 px-4 bg-light">
          <Modal.Title className="fw-bold d-flex align-items-center gap-2" style={{ fontSize: 17 }}>
            <Users size={18} className="text-primary" />
            <span>Driver Record Details</span>
          </Modal.Title>
        </Modal.Header>
        <Modal.Body className="p-4" style={{ maxHeight: 'calc(85vh - 120px)' }}>
          {selectedDriver && (
            <div>
              {/* Profile Card Header */}
              <div 
                className="d-flex flex-wrap align-items-center justify-content-between gap-3 mb-4 p-3 rounded-3 border" 
                style={{ 
                  background: 'linear-gradient(to right, #f8fafc, #f1f5f9)', 
                  borderColor: '#e2e8f0' 
                }}
              >
                <div className="d-flex align-items-center gap-3">
                  <div
                    style={{
                      width: 54,
                      height: 54,
                      borderRadius: 14,
                      background: 'linear-gradient(135deg, #2563eb, #1d4ed8)',
                      color: '#ffffff',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 700,
                      fontSize: 22,
                      boxShadow: '0 4px 12px rgba(37, 99, 235, 0.25)'
                    }}
                  >
                    {selectedDriver.name?.charAt(0) || 'D'}
                  </div>
                  <div>
                    <h5 className="fw-bold text-dark m-0 d-flex align-items-center gap-2">
                      {selectedDriver.name}
                    </h5>
                    <div className="d-flex flex-wrap align-items-center gap-2 mt-1">
                      <span className="badge bg-light text-secondary border font-monospace px-2 py-1" style={{ fontSize: 11 }}>
                        {selectedDriver.code}
                      </span>
                      <span className="text-muted small">
                        Database ID: <strong>#{selectedDriver.id}</strong>
                      </span>
                      <span
                        className="badge"
                        style={{
                          backgroundColor: selectedDriver.active !== false ? '#ecfdf5' : '#f1f5f9',
                          color: selectedDriver.active !== false ? '#059669' : '#64748b',
                          border: `1px solid ${selectedDriver.active !== false ? '#a7f3d0' : '#e2e8f0'}`,
                          fontSize: 10,
                          fontWeight: 700
                        }}
                      >
                        {selectedDriver.active !== false ? 'ACTIVE' : 'INACTIVE'}
                      </span>
                    </div>
                  </div>
                </div>

                {selectedDriver.safetyScore != null && (
                  <div className="text-end ms-auto p-2 bg-white rounded-3 border shadow-sm">
                    <div className="text-muted" style={{ fontSize: 10, textTransform: 'uppercase', letterSpacing: '0.04em' }}>Safety Score</div>
                    <div style={{ fontSize: 22, fontWeight: 800, color: scoreColor(selectedDriver.safetyScore) }}>
                      {typeof selectedDriver.safetyScore === 'number' ? selectedDriver.safetyScore.toFixed(1) : selectedDriver.safetyScore}
                    </div>
                    <div style={{ fontSize: 10, color: scoreColor(selectedDriver.safetyScore), fontWeight: 600 }}>
                      {Number(selectedDriver.safetyScore) >= 85 ? 'Excellent' : Number(selectedDriver.safetyScore) >= 70 ? 'Good' : Number(selectedDriver.safetyScore) >= 50 ? 'Fair' : 'Critical'}
                    </div>
                  </div>
                )}
              </div>

              {/* Information Section: Email, Phone, License, Joined Date */}
              <div className="mb-4">
                <h6 className="fw-bold text-dark mb-2.5 d-flex align-items-center gap-1.5" style={{ fontSize: 13, textTransform: 'uppercase', letterSpacing: '0.03em', color: '#475569' }}>
                  <FileText size={15} className="text-primary" /> Driver Information
                </h6>
                <div className="row g-2.5">
                  <div className="col-12 col-sm-6 col-lg-3">
                    <div className="p-3 bg-light rounded-3 border h-100" style={{ borderColor: '#e2e8f0' }}>
                      <div className="text-muted d-flex align-items-center gap-1 mb-1" style={{ fontSize: 11 }}>
                        <Mail size={12} /> Email Address
                      </div>
                      <div className="text-truncate" style={{ fontSize: 13 }} title={selectedDriver.email || 'N/A'}>
                        {selectedDriver.email ? (
                          <a href={`mailto:${selectedDriver.email}`} className="text-primary fw-semibold text-decoration-none">
                            {selectedDriver.email}
                          </a>
                        ) : (
                          <span className="text-muted fst-italic">N/A</span>
                        )}
                      </div>
                    </div>
                  </div>

                  <div className="col-12 col-sm-6 col-lg-3">
                    <div className="p-3 bg-light rounded-3 border h-100" style={{ borderColor: '#e2e8f0' }}>
                      <div className="text-muted d-flex align-items-center gap-1 mb-1" style={{ fontSize: 11 }}>
                        <Phone size={12} /> Phone Contact
                      </div>
                      <div style={{ fontSize: 13 }}>
                        {selectedDriver.phone ? (
                          <a href={`tel:${selectedDriver.phone}`} className="text-dark font-monospace fw-semibold text-decoration-none">
                            {selectedDriver.phone}
                          </a>
                        ) : (
                          <span className="text-muted fst-italic">N/A</span>
                        )}
                      </div>
                    </div>
                  </div>

                  <div className="col-12 col-sm-6 col-lg-3">
                    <div className="p-3 bg-light rounded-3 border h-100" style={{ borderColor: '#e2e8f0' }}>
                      <div className="text-muted d-flex align-items-center gap-1 mb-1" style={{ fontSize: 11 }}>
                        <Shield size={12} /> License Number
                      </div>
                      <div style={{ fontSize: 13 }}>
                        {selectedDriver.licenseNumber ? (
                          <span className="font-monospace fw-semibold text-dark">
                            {selectedDriver.licenseNumber}
                          </span>
                        ) : (
                          <span className="text-muted fst-italic">N/A</span>
                        )}
                      </div>
                    </div>
                  </div>

                  <div className="col-12 col-sm-6 col-lg-3">
                    <div className="p-3 bg-light rounded-3 border h-100" style={{ borderColor: '#e2e8f0' }}>
                      <div className="text-muted d-flex align-items-center gap-1 mb-1" style={{ fontSize: 11 }}>
                        <Calendar size={12} /> Joined Date
                      </div>
                      <div className="fw-semibold text-dark" style={{ fontSize: 13 }}>
                        {selectedDriver.joinedOn ? (
                          <span>{selectedDriver.joinedOn}</span>
                        ) : (
                          <span className="text-muted fst-italic">N/A</span>
                        )}
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Assigned Vehicles Section */}
              <div className="mb-4">
                <h6 className="fw-bold text-dark mb-2.5 d-flex align-items-center justify-content-between" style={{ fontSize: 13, textTransform: 'uppercase', letterSpacing: '0.03em', color: '#475569' }}>
                  <span className="d-flex align-items-center gap-1.5">
                    <Truck size={15} className="text-primary" /> Currently Assigned Vehicles
                  </span>
                  <span className="badge bg-secondary-subtle text-secondary" style={{ fontSize: 10 }}>
                    {selectedDriver.currentVehicles?.length || selectedDriver.assignedVehicles?.length || 0} Assigned
                  </span>
                </h6>
                {selectedDriver.currentVehicles && selectedDriver.currentVehicles.length > 0 ? (
                  <div className="row g-2">
                    {selectedDriver.currentVehicles.map(veh => (
                      <div key={veh.id} className="col-12 col-sm-6">
                        <div className="p-2.5 rounded-3 border bg-white shadow-sm d-flex align-items-center justify-content-between" style={{ borderColor: '#e2e8f0' }}>
                          <div>
                            <div className="d-flex align-items-center gap-1.5">
                              <span className="badge bg-primary text-white font-monospace" style={{ fontSize: 11 }}>
                                {veh.code}
                              </span>
                              <span className="fw-bold text-dark font-monospace" style={{ fontSize: 12 }}>
                                {veh.registrationNumber}
                              </span>
                            </div>
                            <div className="text-muted mt-1" style={{ fontSize: 11 }}>
                              {veh.makeModel || veh.vehicleType || 'Fleet Vehicle'} {veh.vehicleType && veh.makeModel ? `• ${veh.vehicleType}` : ''}
                            </div>
                          </div>
                          <span className="badge bg-success-subtle text-success border border-success-subtle" style={{ fontSize: 10, fontWeight: 700 }}>
                            CURRENT
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : selectedDriver.assignedVehicles && selectedDriver.assignedVehicles.length > 0 ? (
                  <div className="d-flex flex-wrap gap-2">
                    {selectedDriver.assignedVehicles.map((v, i) => (
                      <span key={i} className="badge bg-success-subtle text-success border border-success-subtle p-2" style={{ fontSize: 12 }}>
                        {v}
                      </span>
                    ))}
                  </div>
                ) : (
                  <div className="text-muted small fst-italic p-2.5 bg-light rounded-3 border text-center">
                    No vehicles currently assigned to this driver.
                  </div>
                )}

                {/* Historical Vehicles Operated (if different from currently assigned) */}
                {selectedDriver.historicalVehicles && selectedDriver.historicalVehicles.length > 0 && (
                  <div className="mt-2.5">
                    <div className="text-muted small mb-1.5 fw-semibold" style={{ fontSize: 11 }}>
                      Previously Operated in Past Trips:
                    </div>
                    <div className="d-flex flex-wrap gap-1.5">
                      {selectedDriver.historicalVehicles.map(h => (
                        <span key={h.id} className="badge bg-light text-secondary border font-monospace" style={{ fontSize: 11 }}>
                          {h.code} ({h.registrationNumber})
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              {/* Statistics Section: 5 Distinct Tiles */}
              <div className="mb-4">
                <h6 className="fw-bold text-dark mb-2.5 d-flex align-items-center gap-1.5" style={{ fontSize: 13, textTransform: 'uppercase', letterSpacing: '0.03em', color: '#475569' }}>
                  <TrendingUp size={15} className="text-primary" /> Lifetime Performance & Safety Metrics
                </h6>
                <div className="row g-2 text-center">
                  {/* Total Trips */}
                  <div className="col-12 col-sm-6 col-md">
                    <div className="p-2.5 bg-light rounded-3 border h-100 d-flex flex-column justify-content-between" style={{ borderColor: '#e2e8f0' }}>
                      <div className="text-muted fw-medium" style={{ fontSize: 11 }}>Total Trips</div>
                      <div className="fw-bold fs-5 text-dark my-1">{selectedDriver.totalTrips || 0}</div>
                      <div className="text-muted" style={{ fontSize: 10 }}>Completed Trips</div>
                    </div>
                  </div>

                  {/* Total Distance */}
                  <div className="col-12 col-sm-6 col-md">
                    <div className="p-2.5 bg-light rounded-3 border h-100 d-flex flex-column justify-content-between" style={{ borderColor: '#e2e8f0' }}>
                      <div className="text-muted fw-medium" style={{ fontSize: 11 }}>Total Distance</div>
                      <div className="fw-bold fs-5 text-dark my-1">
                        {selectedDriver.totalDistanceKm ? `${Number(selectedDriver.totalDistanceKm).toLocaleString(undefined, { maximumFractionDigits: 1 })} km` : '0 km'}
                      </div>
                      <div className="text-muted" style={{ fontSize: 10 }}>Logged Distance</div>
                    </div>
                  </div>

                  {/* Total Alerts */}
                  <div className="col-12 col-sm-6 col-md">
                    <div className="p-2.5 bg-light rounded-3 border h-100 d-flex flex-column justify-content-between" style={{ borderColor: '#e2e8f0' }}>
                      <div className="text-muted fw-medium" style={{ fontSize: 11 }}>Total Alerts</div>
                      <div className="fw-bold fs-5 text-warning my-1">{selectedDriver.totalAlerts || 0}</div>
                      <div className="text-muted" style={{ fontSize: 10 }}>Safety Warnings</div>
                    </div>
                  </div>

                  {/* Penalty Count */}
                  <div className="col-12 col-sm-6 col-md">
                    <div className="p-2.5 bg-light rounded-3 border h-100 d-flex flex-column justify-content-between" style={{ borderColor: '#e2e8f0' }}>
                      <div className="text-muted fw-medium" style={{ fontSize: 11 }}>Penalty Count</div>
                      <div className={`fw-bold fs-5 my-1 ${Number(selectedDriver.totalPenalties || 0) > 0 ? (Number(selectedDriver.totalPenaltyAmount || 0) > 0 ? 'text-danger' : 'text-warning') : 'text-dark'}`}>
                        {selectedDriver.totalPenalties || 0}
                      </div>
                      <div style={{ fontSize: 10 }} className={Number(selectedDriver.totalPenalties || 0) > 0 && Number(selectedDriver.totalPenaltyAmount || 0) === 0 ? "text-warning fw-semibold" : "text-muted"}>
                        {Number(selectedDriver.totalPenalties || 0) === 0 ? '0 Active Records' : Number(selectedDriver.totalPenaltyAmount || 0) === 0 ? 'Active Warning' : `${selectedDriver.totalPenalties} Active Penalties`}
                      </div>
                    </div>
                  </div>

                  {/* Total Penalty Amount */}
                  <div className="col-12 col-sm-6 col-md">
                    <div className="p-2.5 bg-light rounded-3 border h-100 d-flex flex-column justify-content-between" style={{ borderColor: '#e2e8f0' }}>
                      <div className="text-muted fw-medium" style={{ fontSize: 11 }}>Total Penalty Amount</div>
                      <div className={`fw-bold fs-5 my-1 ${Number(selectedDriver.totalPenaltyAmount || 0) > 0 ? 'text-danger' : 'text-dark'}`}>
                        ₹{Number(selectedDriver.totalPenaltyAmount || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                      </div>
                      <div className="text-muted" style={{ fontSize: 10 }}>Applied Fines</div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Penalty History & Incidents (Transparency Section) */}
              <div>
                <h6 className="fw-bold text-dark mb-2 d-flex align-items-center justify-content-between" style={{ fontSize: 13, textTransform: 'uppercase', letterSpacing: '0.03em', color: '#475569' }}>
                  <span className="d-flex align-items-center gap-1.5">
                    <AlertTriangle size={15} className="text-danger" /> Penalty Incident Records
                  </span>
                  <span className="badge bg-light text-secondary border" style={{ fontSize: 10 }}>
                    {selectedDriver.penaltyRecords?.length || 0} Total Records
                  </span>
                </h6>
                {selectedDriver.penaltyRecords && selectedDriver.penaltyRecords.length > 0 ? (
                  <div className="table-responsive border rounded-3 bg-white">
                    <table className="table table-sm table-hover align-middle mb-0" style={{ fontSize: 12 }}>
                      <thead className="table-light">
                        <tr style={{ fontSize: 11, color: '#64748b' }}>
                          <th className="ps-3">Incident Date</th>
                          <th>Classification</th>
                          <th>Reason / Alert Type</th>
                          <th>Points</th>
                          <th>Fine Amount</th>
                          <th className="pe-3 text-end">Status</th>
                        </tr>
                      </thead>
                      <tbody>
                        {selectedDriver.penaltyRecords.map(p => (
                          <tr key={p.id}>
                            <td className="ps-3 font-monospace" style={{ fontSize: 11 }}>
                              {p.dateApplied ? new Date(p.dateApplied).toLocaleDateString() : 'N/A'}
                            </td>
                            <td>
                              <span
                                className={`badge ${
                                  p.classification === 'WARNING'
                                    ? 'bg-warning-subtle text-warning-emphasis border border-warning-subtle'
                                    : 'bg-danger-subtle text-danger border border-danger-subtle'
                                }`}
                                style={{ fontSize: 10 }}
                              >
                                {p.classification || p.penaltyType || 'WARNING'}
                              </span>
                            </td>
                            <td style={{ maxWidth: 220 }} className="text-truncate" title={p.reason}>
                              {p.reason}
                            </td>
                            <td className="font-monospace">
                              {p.penaltyPoints ? `${Number(p.penaltyPoints).toFixed(1)} pts` : '0 pts'}
                            </td>
                            <td className="fw-bold font-monospace">
                              {Number(p.monetaryAmount || 0) > 0 ? (
                                <span className={p.status === 'ACTIVE' ? 'text-danger' : 'text-muted text-decoration-line-through'}>
                                  ₹{Number(p.monetaryAmount).toFixed(2)}
                                </span>
                              ) : (
                                <span className="text-muted">₹0.00</span>
                              )}
                            </td>
                            <td className="pe-3 text-end">
                              <span
                                className={`badge ${
                                  p.status === 'ACTIVE'
                                    ? 'bg-success-subtle text-success border border-success-subtle'
                                    : 'bg-secondary-subtle text-secondary border'
                                }`}
                                style={{ fontSize: 10 }}
                              >
                                {p.status || 'ACTIVE'}
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <div className="p-3 bg-success-subtle rounded-3 border border-success-subtle text-success small d-flex align-items-center gap-2">
                    <CheckCircle2 size={16} />
                    <span>Clear Record: No safety infractions or penalties recorded for this driver in the database.</span>
                  </div>
                )}
              </div>
            </div>
          )}
        </Modal.Body>
        <Modal.Footer className="border-top py-2.5 px-4 bg-light d-flex justify-content-between">
          <Button
            variant="outline-primary"
            size="sm"
            onClick={() => {
              setShowViewModal(false);
              if (selectedDriver) handleOpenEdit(selectedDriver);
            }}
            className="d-flex align-items-center gap-1.5"
            style={{ borderRadius: 7 }}
          >
            <Edit3 size={13} />
            <span>Edit Driver</span>
          </Button>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setShowViewModal(false)}
            style={{ borderRadius: 7 }}
          >
            Close
          </Button>
        </Modal.Footer>
      </Modal>

      {/* ADD DRIVER MODAL */}
      <Modal show={showAddModal} onHide={() => setShowAddModal(false)} centered>
        <Modal.Header closeButton>
          <Modal.Title className="fw-bold" style={{ fontSize: 18 }}>
            Add New Driver
          </Modal.Title>
        </Modal.Header>
        <Form onSubmit={handleSubmitAdd}>
          <Modal.Body>
            {modalError && <Alert variant="danger" className="py-2 small">{modalError}</Alert>}

            <Form.Group className="mb-3">
              <Form.Label className="small fw-semibold">Driver Code *</Form.Label>
              <Form.Control
                type="text"
                placeholder="e.g. DRV022"
                value={formData.code}
                onChange={e => setFormData({ ...formData, code: e.target.value })}
                required
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label className="small fw-semibold">Full Name *</Form.Label>
              <Form.Control
                type="text"
                placeholder="Enter driver's full name"
                value={formData.name}
                onChange={e => setFormData({ ...formData, name: e.target.value })}
                required
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label className="small fw-semibold">Email Address</Form.Label>
              <Form.Control
                type="email"
                placeholder="e.g. driver@fleettrack.io"
                value={formData.email}
                onChange={e => setFormData({ ...formData, email: e.target.value })}
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label className="small fw-semibold">Phone Number</Form.Label>
              <Form.Control
                type="text"
                placeholder="e.g. 9876543210"
                value={formData.phone}
                onChange={e => setFormData({ ...formData, phone: e.target.value })}
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label className="small fw-semibold">License Number</Form.Label>
              <Form.Control
                type="text"
                placeholder="e.g. MH2024DL0099"
                value={formData.licenseNumber}
                onChange={e => setFormData({ ...formData, licenseNumber: e.target.value })}
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label className="small fw-semibold">Joined Date</Form.Label>
              <Form.Control
                type="date"
                value={formData.joinedOn}
                onChange={e => setFormData({ ...formData, joinedOn: e.target.value })}
              />
            </Form.Group>

            <Form.Check
              type="switch"
              id="add-driver-active"
              label="Active Status"
              checked={formData.active}
              onChange={e => setFormData({ ...formData, active: e.target.checked })}
            />
          </Modal.Body>
          <Modal.Footer>
            <Button variant="secondary" size="sm" onClick={() => setShowAddModal(false)}>
              Cancel
            </Button>
            <Button variant="primary" size="sm" type="submit" disabled={actionLoading}>
              {actionLoading ? 'Saving…' : 'Save Driver'}
            </Button>
          </Modal.Footer>
        </Form>
      </Modal>

      {/* EDIT DRIVER MODAL */}
      <Modal show={showEditModal} onHide={() => setShowEditModal(false)} centered>
        <Modal.Header closeButton>
          <Modal.Title className="fw-bold" style={{ fontSize: 18 }}>
            Edit Driver Record #{selectedDriver?.id}
          </Modal.Title>
        </Modal.Header>
        <Form onSubmit={handleSubmitEdit}>
          <Modal.Body>
            {modalError && <Alert variant="danger" className="py-2 small">{modalError}</Alert>}

            <Form.Group className="mb-3">
              <Form.Label className="small fw-semibold">Driver Code *</Form.Label>
              <Form.Control
                type="text"
                value={formData.code}
                onChange={e => setFormData({ ...formData, code: e.target.value })}
                required
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label className="small fw-semibold">Full Name *</Form.Label>
              <Form.Control
                type="text"
                value={formData.name}
                onChange={e => setFormData({ ...formData, name: e.target.value })}
                required
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label className="small fw-semibold">Email Address</Form.Label>
              <Form.Control
                type="email"
                value={formData.email}
                onChange={e => setFormData({ ...formData, email: e.target.value })}
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label className="small fw-semibold">Phone Number</Form.Label>
              <Form.Control
                type="text"
                value={formData.phone}
                onChange={e => setFormData({ ...formData, phone: e.target.value })}
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label className="small fw-semibold">License Number</Form.Label>
              <Form.Control
                type="text"
                value={formData.licenseNumber}
                onChange={e => setFormData({ ...formData, licenseNumber: e.target.value })}
              />
            </Form.Group>

            <Form.Group className="mb-3">
              <Form.Label className="small fw-semibold">Joined Date</Form.Label>
              <Form.Control
                type="date"
                value={formData.joinedOn}
                onChange={e => setFormData({ ...formData, joinedOn: e.target.value })}
              />
            </Form.Group>

            <Form.Check
              type="switch"
              id="edit-driver-active"
              label="Active Status"
              checked={formData.active}
              onChange={e => setFormData({ ...formData, active: e.target.checked })}
            />
          </Modal.Body>
          <Modal.Footer>
            <Button variant="secondary" size="sm" onClick={() => setShowEditModal(false)}>
              Cancel
            </Button>
            <Button variant="primary" size="sm" type="submit" disabled={actionLoading}>
              {actionLoading ? 'Updating…' : 'Update Driver'}
            </Button>
          </Modal.Footer>
        </Form>
      </Modal>

      {/* DELETE CONFIRMATION MODAL */}
      <Modal show={showDeleteModal} onHide={() => setShowDeleteModal(false)} centered size="sm">
        <Modal.Header closeButton>
          <Modal.Title className="fw-bold text-danger" style={{ fontSize: 16 }}>
            Confirm Delete
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {modalError && <Alert variant="danger" className="py-2 small">{modalError}</Alert>}
          <p className="small text-muted mb-0">
            Are you sure you want to delete driver <strong>{selectedDriver?.name}</strong> ({selectedDriver?.code})?
            Any currently assigned vehicles will be unlinked safely.
          </p>
        </Modal.Body>
        <Modal.Footer>
          <Button variant="secondary" size="sm" onClick={() => setShowDeleteModal(false)}>
            Cancel
          </Button>
          <Button variant="danger" size="sm" onClick={handleConfirmDelete} disabled={actionLoading}>
            {actionLoading ? 'Deleting…' : 'Delete'}
          </Button>
        </Modal.Footer>
      </Modal>
    </div>
  );
}
