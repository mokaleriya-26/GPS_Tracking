import React, { useState } from 'react';
import jsPDF from 'jspdf';

import {
  Car,
  CalendarDays,
  BarChart3,
  CalendarRange,
  SlidersHorizontal
} from 'lucide-react';

const Reports = ({ vehicles = [] }) => {
  const [reportType, setReportType] = useState('Trip');

  const [selectedDriver, setSelectedDriver] = useState('');
  const [selectedTrip, setSelectedTrip] = useState('');

  const [selectedDate, setSelectedDate] = useState('');
  const [selectedWeek, setSelectedWeek] = useState('');
  const [selectedMonth, setSelectedMonth] = useState('');

  const [customFrom, setCustomFrom] = useState('');
  const [customTo, setCustomTo] = useState('');

  /*
   * ---------------------------------------------------------
   * DRIVER DATA
   * ---------------------------------------------------------
   * Driver names are taken from the existing vehicle data.
   */

  const drivers = [
    ...new Map(
      vehicles
        .map((vehicle) => {
          const driverValue = vehicle.driver;

          const driverId =
            vehicle.driverId ||
            vehicle.driver_id ||
            (typeof driverValue === 'object'
              ? driverValue?.id
              : driverValue);

          const driverName =
            vehicle.driverName ||
            vehicle.driver_name ||
            (typeof driverValue === 'object'
              ? driverValue?.name
              : driverValue) ||
            vehicle.name ||
            driverId;

          if (!driverId && !driverName) {
            return null;
          }

          return [
            String(driverId),
            String(driverName)
          ];
        })
        .filter(Boolean)
    ).entries()
  ];

  /*
   * ---------------------------------------------------------
   * DEMO TRIPS
   * ---------------------------------------------------------
   */

  const trips = [
    {
      id: 'trip-001',
      name: 'Mumbai → Pune'
    },
    {
      id: 'trip-002',
      name: 'Pune → Mumbai'
    },
    {
      id: 'trip-003',
      name: 'Navi Mumbai → Thane'
    }
  ];

  /*
   * ---------------------------------------------------------
   * REPORT TYPES
   * ---------------------------------------------------------
   */

  const reportTypes = [
    {
      name: 'Trip',
      description: 'Detailed report for a single trip',
      icon: <Car size={26} />
    },
    {
      name: 'Daily',
      description: 'Complete performance for one day',
      icon: <CalendarDays size={26} />
    },
    {
      name: 'Weekly',
      description: 'Performance summary for one week',
      icon: <BarChart3 size={26} />
    },
    {
      name: 'Monthly',
      description: 'Complete monthly performance',
      icon: <CalendarRange size={26} />
    },
    {
      name: 'Custom',
      description: 'Build a report with your own filters',
      icon: <SlidersHorizontal size={26} />
    }
  ];

  /*
   * ---------------------------------------------------------
   * SELECTED DRIVER
   * ---------------------------------------------------------
   */

  const selectedDriverName =
    drivers.find(([id]) => String(id) === String(selectedDriver))?.[1] ||
    'All Drivers';

  /*
   * ---------------------------------------------------------
   * SELECTED TRIP
   * ---------------------------------------------------------
   */

  const selectedTripName =
    trips.find((trip) => trip.id === selectedTrip)?.name ||
    'Not Selected';

  /*
   * ---------------------------------------------------------
   * GET VEHICLE OF SELECTED DRIVER
   * ---------------------------------------------------------
   */

  const selectedVehicle =
    vehicles.find((vehicle) => {
      if (!selectedDriver) {
        return false;
      }

      const driverValue = vehicle.driver;

      const driverId =
        vehicle.driverId ||
        vehicle.driver_id ||
        (typeof driverValue === 'object'
          ? driverValue?.id
          : driverValue);

      return String(driverId) === String(selectedDriver);
    }) || vehicles[0] || {};

  const vehicleId =
    selectedVehicle.vehicleId ||
    selectedVehicle.vehicle_id ||
    selectedVehicle.id ||
    'VH001';

  const vehicleNumber =
    selectedVehicle.vehicleNumber ||
    selectedVehicle.vehicle_number ||
    selectedVehicle.registrationNumber ||
    selectedVehicle.registration_number ||
    'MH04AB1234';

  /*
   * ---------------------------------------------------------
   * REPORT DATE
   * ---------------------------------------------------------
   */

  const getReportPeriod = () => {
    if (reportType === 'Trip') {
      return 'Selected Trip';
    }

    if (reportType === 'Daily') {
      return selectedDate || 'Today';
    }

    if (reportType === 'Weekly') {
      return selectedWeek || 'Current Week';
    }

    if (reportType === 'Monthly') {
      return selectedMonth || 'Current Month';
    }

    if (reportType === 'Custom') {
      if (customFrom && customTo) {
        return `${customFrom} to ${customTo}`;
      }

      return 'Custom Date Range';
    }

    return '';
  };

  /*
   * ---------------------------------------------------------
   * GENERATE PDF
   * ---------------------------------------------------------
   */

  const generatePDF = () => {
    const doc = new jsPDF();

    const generatedDate = new Date().toLocaleDateString('en-IN');

    /*
     * -------------------------------------------------------
     * DEMO VALUES
     * -------------------------------------------------------
     */

    const demoData = {
      distance: '426.2 km',
      drivingTime: '7h 15m',
      averageSpeed: '58 km/h',
      maximumSpeed: '112 km/h',
      performanceScore: '86 / 100',

      totalTrips: '8',
      totalAlerts: '5',

      overspeed: '2',
      harshBraking: '1',
      gpsDisconnect: '1',
      ignition: '1',
      nightDriving: '0'
    };

    /*
     * -------------------------------------------------------
     * PAGE SETTINGS
     * -------------------------------------------------------
     */

    const pageWidth = doc.internal.pageSize.getWidth();

    const left = 18;
    const right = pageWidth - 18;

    /*
     * -------------------------------------------------------
     * HEADER
     * -------------------------------------------------------
     */

    doc.setFont('helvetica', 'bold');
    doc.setFontSize(22);

    doc.text('TrackFleet', left, 20);

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(10);

    doc.text(
      'Fleet Management System',
      left,
      27
    );

    doc.setLineWidth(0.7);
    doc.line(left, 33, right, 33);

    /*
     * -------------------------------------------------------
     * REPORT TITLE
     * -------------------------------------------------------
     */

    doc.setFont('helvetica', 'bold');
    doc.setFontSize(17);

    doc.text(
      `${reportType} Report`,
      left,
      45
    );

    /*
     * -------------------------------------------------------
     * BASIC INFORMATION
     * -------------------------------------------------------
     */

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(10.5);

    doc.text(
      `Driver: ${selectedDriverName}`,
      left,
      55
    );

    doc.text(
      `Vehicle: ${vehicleNumber}`,
      left,
      62
    );

    doc.text(
      `Reporting Period: ${getReportPeriod()}`,
      left,
      69
    );

    doc.text(
      `Generated: ${generatedDate}`,
      right,
      55,
      { align: 'right' }
    );

    /*
     * -------------------------------------------------------
     * PERFORMANCE SUMMARY
     * -------------------------------------------------------
     */

    doc.setFont('helvetica', 'bold');
    doc.setFontSize(12);

    doc.text(
      'Performance Summary',
      left,
      84
    );

    doc.setLineWidth(0.3);
    doc.line(left, 87, right, 87);

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(10.5);

    /*
     * First row
     */

    doc.text(
      `Distance: ${demoData.distance}`,
      left,
      98
    );

    doc.text(
      `Driving Time: ${demoData.drivingTime}`,
      75,
      98
    );

    doc.text(
      `Avg. Speed: ${demoData.averageSpeed}`,
      140,
      98
    );

    /*
     * Second row
     */

    doc.text(
      `Max Speed: ${demoData.maximumSpeed}`,
      left,
      106
    );

    doc.text(
      `Performance Score: ${demoData.performanceScore}`,
      75,
      106
    );

    /*
     * -------------------------------------------------------
     * TRIP SUMMARY
     * -------------------------------------------------------
     */

    doc.setFont('helvetica', 'bold');
    doc.setFontSize(12);

    doc.text(
      'Trip Summary',
      left,
      124
    );

    doc.line(left, 127, right, 127);

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(10.5);

    doc.text(
      `Trips Completed: ${demoData.totalTrips}`,
      left,
      138
    );

    doc.text(
      `Total Distance: ${demoData.distance}`,
      105,
      138
    );

    /*
     * -------------------------------------------------------
     * SELECTED TRIP
     * -------------------------------------------------------
     */

    if (reportType === 'Trip') {
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(12);

      doc.text(
        'Trip Details',
        left,
        154
      );

      doc.line(left, 157, right, 157);

      doc.setFont('helvetica', 'normal');
      doc.setFontSize(10.5);

      doc.text(
        `Route: ${selectedTripName}`,
        left,
        168
      );

      doc.text(
        'Start Time: 08:30 AM',
        left,
        176
      );

      doc.text(
        'End Time: 12:15 PM',
        105,
        176
      );

      doc.text(
        'Duration: 3h 45m',
        left,
        184
      );

      doc.text(
        'Distance: 148.6 km',
        105,
        184
      );
    }

    /*
     * -------------------------------------------------------
     * ALERT SUMMARY
     * -------------------------------------------------------
     */

    const alertStartY =
      reportType === 'Trip'
        ? 201
        : 154;

    doc.setFont('helvetica', 'bold');
    doc.setFontSize(12);

    doc.text(
      'Alert Summary',
      left,
      alertStartY
    );

    doc.line(
      left,
      alertStartY + 3,
      right,
      alertStartY + 3
    );

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(10.5);

    const alertY = alertStartY + 15;

    /*
     * Alert row 1
     */

    doc.text(
      `Total Alerts: ${demoData.totalAlerts}`,
      left,
      alertY
    );

    doc.text(
      `Overspeed: ${demoData.overspeed}`,
      105,
      alertY
    );

    /*
     * Alert row 2
     */

    doc.text(
      `Harsh Braking: ${demoData.harshBraking}`,
      left,
      alertY + 9
    );

    doc.text(
      `GPS Disconnect: ${demoData.gpsDisconnect}`,
      105,
      alertY + 9
    );

    /*
     * Alert row 3
     */

    doc.text(
      `Ignition: ${demoData.ignition}`,
      left,
      alertY + 18
    );

    doc.text(
      `Night Driving: ${demoData.nightDriving}`,
      105,
      alertY + 18
    );

    /*
     * -------------------------------------------------------
     * RECENT ALERTS
     * -------------------------------------------------------
     */

    const recentAlertY = alertY + 36;

    doc.setFont('helvetica', 'bold');
    doc.setFontSize(12);

    doc.text(
      'Recent Alerts',
      left,
      recentAlertY
    );

    doc.line(
      left,
      recentAlertY + 3,
      right,
      recentAlertY + 3
    );

    /*
     * Table Header
     */

    const tableY = recentAlertY + 14;

    doc.setFont('helvetica', 'bold');
    doc.setFontSize(9.5);

    doc.text('Time', left, tableY);
    doc.text('Alert Type', 48, tableY);
    doc.text('Location', 105, tableY);
    doc.text('Speed', 160, tableY);

    doc.line(
      left,
      tableY + 3,
      right,
      tableY + 3
    );

    /*
     * Alert rows
     */

    const alerts = [
      ['09:42 AM', 'Overspeed', 'Mumbai-Pune Hwy', '112 km/h'],
      ['10:18 AM', 'Harsh Braking', 'Lonavala', '71 km/h'],
      ['11:05 AM', 'GPS Disconnect', 'Pune', '--']
    ];

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9.5);

    alerts.forEach((alert, index) => {
      const rowY = tableY + 13 + index * 10;

      doc.text(alert[0], left, rowY);
      doc.text(alert[1], 48, rowY);
      doc.text(alert[2], 105, rowY);
      doc.text(alert[3], 160, rowY);
    });

    /*
     * -------------------------------------------------------
     * FOOTER
     * -------------------------------------------------------
     */

    doc.setLineWidth(0.4);

    doc.line(
      left,
      275,
      right,
      275
    );

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(8.5);

    doc.text(
      'TrackFleet',
      left,
      283
    );

    doc.text(
      `Generated ${generatedDate}`,
      right,
      283,
      { align: 'right' }
    );

    /*
     * -------------------------------------------------------
     * SAVE
     * -------------------------------------------------------
     */

    const safeReportType =
      reportType.replace(/\s+/g, '-');

    doc.save(
      `TrackFleet-${safeReportType}-Report.pdf`
    );
  };

  /*
   * ---------------------------------------------------------
   * RESET
   * ---------------------------------------------------------
   */

  const resetFilters = () => {
    setSelectedDriver('');
    setSelectedTrip('');
    setSelectedDate('');
    setSelectedWeek('');
    setSelectedMonth('');
    setCustomFrom('');
    setCustomTo('');
  };

  /*
   * ---------------------------------------------------------
   * UI
   * ---------------------------------------------------------
   */

  return (
    <div>

      {/* PAGE HEADER */}

      <div className="mb-4">

        <h1 className="fw-bold mb-2">
          Reports
        </h1>

        <p className="text-muted mb-0">
          Generate fleet performance reports.
        </p>

      </div>

      {/* MAIN CARD */}

      <div className="bg-white rounded-4 shadow-sm p-4 mb-4">

        {/* HEADER */}

        <div className="mb-4">

          <h3 className="fw-bold mb-1">
            Create Report
          </h3>

          <p className="text-muted mb-0">
            Select a report type and generate a PDF report.
          </p>

        </div>

        {/* REPORT TYPES */}

        <h5 className="fw-semibold mb-3">
          Report Type
        </h5>

        <div className="row g-3">

          {reportTypes.map((report) => {

            const isSelected =
              reportType === report.name;

            return (

              <div
                className="col-md-6 col-lg"
                key={report.name}
              >

                <button
                  type="button"
                  onClick={() => {

                    setReportType(report.name);

                    setSelectedTrip('');
                    setSelectedDate('');
                    setSelectedWeek('');
                    setSelectedMonth('');
                    setCustomFrom('');
                    setCustomTo('');
                  }}
                  className={`w-100 text-start border rounded-3 p-3 h-100 ${
                    isSelected
                      ? 'border-primary bg-primary-subtle'
                      : 'bg-white'
                  }`}
                  style={{
                    transition: 'all 0.2s ease',
                    cursor: 'pointer'
                  }}
                >

                  <div
                    className={`mb-3 ${
                      isSelected
                        ? 'text-primary'
                        : 'text-secondary'
                    }`}
                  >
                    {report.icon}
                  </div>

                  <h5 className="fw-bold mb-1">
                    {report.name}
                  </h5>

                  <p className="text-muted small mb-0">
                    {report.description}
                  </p>

                </button>

              </div>

            );

          })}

        </div>

        {/* OPTIONS */}

        <div className="border-top mt-4 pt-4">

          <h4 className="fw-bold mb-1">
            {reportType} Report
          </h4>

          <p className="text-muted mb-4">

            {reportType === 'Trip' &&
              'Generate a detailed report for a specific journey.'}

            {reportType === 'Daily' &&
              'Generate the overall performance report for one day.'}

            {reportType === 'Weekly' &&
              'Generate the overall performance report for one week.'}

            {reportType === 'Monthly' &&
              'Generate the overall performance report for one month.'}

            {reportType === 'Custom' &&
              'Generate a report using a custom date range.'}

          </p>

          {/* DRIVER */}

          <div className="row g-4">

            <div className="col-md-6">

              <label className="form-label fw-semibold">
                Driver
              </label>

              <select
                className="form-select form-select-lg"
                value={selectedDriver}
                onChange={(e) =>
                  setSelectedDriver(e.target.value)
                }
              >

                <option value="">
                  Select Driver
                </option>

                {drivers.map(
                  ([driverId, driverName]) => (

                    <option
                      key={driverId}
                      value={driverId}
                    >
                      {driverName}
                    </option>

                  )
                )}

              </select>

            </div>

            {/* TRIP */}

            {reportType === 'Trip' && (

              <div className="col-md-6">

                <label className="form-label fw-semibold">
                  Trip
                </label>

                <select
                  className="form-select form-select-lg"
                  value={selectedTrip}
                  onChange={(e) =>
                    setSelectedTrip(e.target.value)
                  }
                >

                  <option value="">
                    Select Trip
                  </option>

                  {trips.map((trip) => (

                    <option
                      key={trip.id}
                      value={trip.id}
                    >
                      {trip.name}
                    </option>

                  ))}

                </select>

              </div>

            )}

            {/* DAILY */}

            {reportType === 'Daily' && (

              <div className="col-md-6">

                <label className="form-label fw-semibold">
                  Select Date
                </label>

                <input
                  type="date"
                  className="form-control form-control-lg"
                  value={selectedDate}
                  onChange={(e) =>
                    setSelectedDate(e.target.value)
                  }
                />

              </div>

            )}

            {/* WEEKLY */}

            {reportType === 'Weekly' && (

              <div className="col-md-6">

                <label className="form-label fw-semibold">
                  Select Week
                </label>

                <input
                  type="week"
                  className="form-control form-control-lg"
                  value={selectedWeek}
                  onChange={(e) =>
                    setSelectedWeek(e.target.value)
                  }
                />

              </div>

            )}

            {/* MONTHLY */}

            {reportType === 'Monthly' && (

              <div className="col-md-6">

                <label className="form-label fw-semibold">
                  Select Month
                </label>

                <input
                  type="month"
                  className="form-control form-control-lg"
                  value={selectedMonth}
                  onChange={(e) =>
                    setSelectedMonth(e.target.value)
                  }
                />

              </div>

            )}

          </div>

          {/* CUSTOM */}

          {reportType === 'Custom' && (

            <div className="mt-4">

              <h5 className="fw-bold mb-3">
                Date Range
              </h5>

              <div className="row g-4">

                <div className="col-md-6">

                  <label className="form-label fw-semibold">
                    From
                  </label>

                  <input
                    type="date"
                    className="form-control form-control-lg"
                    value={customFrom}
                    onChange={(e) =>
                      setCustomFrom(e.target.value)
                    }
                  />

                </div>

                <div className="col-md-6">

                  <label className="form-label fw-semibold">
                    To
                  </label>

                  <input
                    type="date"
                    className="form-control form-control-lg"
                    value={customTo}
                    onChange={(e) =>
                      setCustomTo(e.target.value)
                    }
                  />

                </div>

              </div>

            </div>

          )}

          {/* BUTTONS */}

          <div className="d-flex justify-content-end gap-3 mt-4">

            <button
              type="button"
              className="btn btn-outline-secondary px-4 py-2"
              onClick={resetFilters}
            >
              Reset
            </button>

            <button
              type="button"
              className="btn btn-primary px-4 py-2"
              onClick={generatePDF}
            >
              Generate PDF Report
            </button>

          </div>

        </div>

      </div>

    </div>
  );
};

export default Reports;