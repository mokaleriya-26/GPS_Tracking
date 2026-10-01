import React, { useState, useRef } from 'react';
import jsPDF from 'jspdf';

import {
  Car,
  CalendarDays,
  BarChart3,
  CalendarRange,
  SlidersHorizontal,
  Download
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

  const [showPreview, setShowPreview] = useState(false);
  const [reportPreview, setReportPreview] = useState(null);
  const previewRef = useRef(null);

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
   * REPORT DEMO DATA
   * ---------------------------------------------------------
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

  const recentAlerts = [
    ['09:42 AM', 'Overspeed', 'Mumbai-Pune Hwy', '112 km/h'],
    ['10:18 AM', 'Harsh Braking', 'Lonavala', '71 km/h'],
    ['11:05 AM', 'GPS Disconnect', 'Pune', '--']
  ];

  /*
   * ---------------------------------------------------------
   * WORKFLOW: GENERATE REPORT (IN-WEBSITE PREVIEW)
   * ---------------------------------------------------------
   */

  const handleGenerateReport = () => {
    const generatedData = {
      reportType,
      driverName: selectedDriverName,
      vehicleNumber,
      period: getReportPeriod(),
      generatedDate: new Date().toLocaleDateString('en-IN'),
      tripName: selectedTripName,
      demoData: { ...demoData },
      alerts: recentAlerts.map((row) => [...row])
    };

    setReportPreview(generatedData);
    setShowPreview(true);

    setTimeout(() => {
      if (previewRef.current) {
        previewRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }, 50);
  };

  /*
   * ---------------------------------------------------------
   * WORKFLOW: MANUAL DOWNLOAD PDF
   * ---------------------------------------------------------
   */

  const downloadPDF = (reportData) => {
    if (!reportData) return;

    const doc = new jsPDF();
    const left = 16;
    const right = 194;
    const contentWidth = 178;
    const rowHeight = 7.5;

    // Helper: Draw Section Header with subtle separator
    const drawSectionHeader = (title, y) => {
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(11);
      doc.setTextColor(15, 23, 42);
      doc.text(title, left, y);

      doc.setDrawColor(226, 232, 240);
      doc.setLineWidth(0.3);
      doc.line(left, y + 2.5, right, y + 2.5);
    };

    // Helper: Fill cell background
    const fillCell = (x, y, w, h, isHeader = false) => {
      if (isHeader) {
        doc.setFillColor(248, 250, 252);
      } else {
        doc.setFillColor(255, 255, 255);
      }
      doc.rect(x, y, w, h, 'F');
    };

    // Helper: Draw cell text
    const drawCellText = (text, x, y, isHeader = false) => {
      if (isHeader) {
        doc.setFont('helvetica', 'bold');
        doc.setFontSize(8.5);
        doc.setTextColor(71, 85, 105);
      } else {
        doc.setFont('helvetica', 'normal');
        doc.setFontSize(9);
        doc.setTextColor(15, 23, 42);
      }
      doc.text(String(text ?? ''), x + 3.5, y + 5.1);
    };

    let currentY = 18;

    /*
     * -------------------------------------------------------
     * HEADER
     * -------------------------------------------------------
     */
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(20);
    doc.setTextColor(30, 41, 59);
    doc.text('TrackFleet', left, currentY);

    currentY += 6;
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(9.5);
    doc.setTextColor(100, 116, 139);
    doc.text('Fleet Management System', left, currentY);

    currentY += 5;
    doc.setDrawColor(203, 213, 225);
    doc.setLineWidth(0.6);
    doc.line(left, currentY, right, currentY);

    /*
     * -------------------------------------------------------
     * REPORT TITLE
     * -------------------------------------------------------
     */
    currentY += 10;
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(15);
    doc.setTextColor(15, 23, 42);
    doc.text(`${reportData.reportType} Report`, left, currentY);

    /*
     * -------------------------------------------------------
     * BASIC INFORMATION
     * -------------------------------------------------------
     */
    currentY += 5;
    const basicY = currentY;
    const basicH = rowHeight * 2;

    fillCell(left, basicY, 26, rowHeight, true);
    fillCell(left + 26, basicY, 63, rowHeight, false);
    fillCell(left + 89, basicY, 26, rowHeight, true);
    fillCell(left + 115, basicY, 63, rowHeight, false);

    fillCell(left, basicY + rowHeight, 32, rowHeight, true);
    fillCell(left + 32, basicY + rowHeight, 57, rowHeight, false);
    fillCell(left + 89, basicY + rowHeight, 26, rowHeight, true);
    fillCell(left + 115, basicY + rowHeight, 63, rowHeight, false);

    drawCellText('Driver:', left, basicY, true);
    drawCellText(reportData.driverName, left + 26, basicY, false);
    drawCellText('Vehicle:', left + 89, basicY, true);
    drawCellText(reportData.vehicleNumber, left + 115, basicY, false);

    drawCellText('Reporting Period:', left, basicY + rowHeight, true);
    drawCellText(reportData.period, left + 32, basicY + rowHeight, false);
    drawCellText('Generated:', left + 89, basicY + rowHeight, true);
    drawCellText(reportData.generatedDate, left + 115, basicY + rowHeight, false);

    doc.setDrawColor(203, 213, 225);
    doc.setLineWidth(0.3);
    doc.rect(left, basicY, contentWidth, basicH);
    doc.line(left, basicY + rowHeight, right, basicY + rowHeight);
    doc.line(left + 89, basicY, left + 89, basicY + basicH);
    doc.line(left + 26, basicY, left + 26, basicY + rowHeight);
    doc.line(left + 115, basicY, left + 115, basicY + basicH);
    doc.line(left + 32, basicY + rowHeight, left + 32, basicY + basicH);

    currentY += basicH;

    /*
     * -------------------------------------------------------
     * PERFORMANCE SUMMARY
     * -------------------------------------------------------
     */
    currentY += 8;
    drawSectionHeader('Performance Summary', currentY);
    currentY += 5;

    const perfY = currentY;
    const perfH = rowHeight * 2;
    const perfColW = contentWidth / 3;

    fillCell(left, perfY, 22, rowHeight, true);
    fillCell(left + 22, perfY, perfColW - 22, rowHeight, false);
    fillCell(left + perfColW, perfY, 26, rowHeight, true);
    fillCell(left + perfColW + 26, perfY, perfColW - 26, rowHeight, false);
    fillCell(left + perfColW * 2, perfY, 24, rowHeight, true);
    fillCell(left + perfColW * 2 + 24, perfY, perfColW - 24, rowHeight, false);

    fillCell(left, perfY + rowHeight, 24, rowHeight, true);
    fillCell(left + 24, perfY + rowHeight, perfColW - 24, rowHeight, false);
    fillCell(left + perfColW, perfY + rowHeight, 35, rowHeight, true);
    fillCell(left + perfColW + 35, perfY + rowHeight, perfColW * 2 - 35, rowHeight, false);

    drawCellText('Distance:', left, perfY, true);
    drawCellText(reportData.demoData.distance, left + 22, perfY, false);
    drawCellText('Driving Time:', left + perfColW, perfY, true);
    drawCellText(reportData.demoData.drivingTime, left + perfColW + 26, perfY, false);
    drawCellText('Avg. Speed:', left + perfColW * 2, perfY, true);
    drawCellText(reportData.demoData.averageSpeed, left + perfColW * 2 + 24, perfY, false);

    drawCellText('Max Speed:', left, perfY + rowHeight, true);
    drawCellText(reportData.demoData.maximumSpeed, left + 24, perfY + rowHeight, false);
    drawCellText('Performance Score:', left + perfColW, perfY + rowHeight, true);
    drawCellText(reportData.demoData.performanceScore, left + perfColW + 35, perfY + rowHeight, false);

    doc.setDrawColor(203, 213, 225);
    doc.setLineWidth(0.3);
    doc.rect(left, perfY, contentWidth, perfH);
    doc.line(left, perfY + rowHeight, right, perfY + rowHeight);
    doc.line(left + perfColW, perfY, left + perfColW, perfY + perfH);
    doc.line(left + perfColW * 2, perfY, left + perfColW * 2, perfY + rowHeight);
    doc.line(left + 22, perfY, left + 22, perfY + rowHeight);
    doc.line(left + perfColW + 26, perfY, left + perfColW + 26, perfY + rowHeight);
    doc.line(left + perfColW * 2 + 24, perfY, left + perfColW * 2 + 24, perfY + rowHeight);
    doc.line(left + 24, perfY + rowHeight, left + 24, perfY + perfH);
    doc.line(left + perfColW + 35, perfY + rowHeight, left + perfColW + 35, perfY + perfH);

    currentY += perfH;

    /*
     * -------------------------------------------------------
     * TRIP SUMMARY
     * -------------------------------------------------------
     */
    currentY += 8;
    drawSectionHeader('Trip Summary', currentY);
    currentY += 5;

    const tripSumY = currentY;
    const tripSumH = rowHeight;
    const halfW = contentWidth / 2;

    fillCell(left, tripSumY, 34, tripSumH, true);
    fillCell(left + 34, tripSumY, halfW - 34, tripSumH, false);
    fillCell(left + halfW, tripSumY, 30, tripSumH, true);
    fillCell(left + halfW + 30, tripSumY, halfW - 30, tripSumH, false);

    drawCellText('Trips Completed:', left, tripSumY, true);
    drawCellText(reportData.demoData.totalTrips, left + 34, tripSumY, false);
    drawCellText('Total Distance:', left + halfW, tripSumY, true);
    drawCellText(reportData.demoData.distance, left + halfW + 30, tripSumY, false);

    doc.setDrawColor(203, 213, 225);
    doc.setLineWidth(0.3);
    doc.rect(left, tripSumY, contentWidth, tripSumH);
    doc.line(left + halfW, tripSumY, left + halfW, tripSumY + tripSumH);
    doc.line(left + 34, tripSumY, left + 34, tripSumY + tripSumH);
    doc.line(left + halfW + 30, tripSumY, left + halfW + 30, tripSumY + tripSumH);

    currentY += tripSumH;

    /*
     * -------------------------------------------------------
     * TRIP DETAILS (if reportType === 'Trip')
     * -------------------------------------------------------
     */
    if (reportData.reportType === 'Trip') {
      currentY += 8;
      drawSectionHeader('Trip Details', currentY);
      currentY += 5;

      const tripDetY = currentY;
      const tripDetH = rowHeight * 3;

      fillCell(left, tripDetY, 24, rowHeight, true);
      fillCell(left + 24, tripDetY, contentWidth - 24, rowHeight, false);

      fillCell(left, tripDetY + rowHeight, 24, rowHeight, true);
      fillCell(left + 24, tripDetY + rowHeight, halfW - 24, rowHeight, false);
      fillCell(left + halfW, tripDetY + rowHeight, 24, rowHeight, true);
      fillCell(left + halfW + 24, tripDetY + rowHeight, halfW - 24, rowHeight, false);

      fillCell(left, tripDetY + rowHeight * 2, 24, rowHeight, true);
      fillCell(left + 24, tripDetY + rowHeight * 2, halfW - 24, rowHeight, false);
      fillCell(left + halfW, tripDetY + rowHeight * 2, 24, rowHeight, true);
      fillCell(left + halfW + 24, tripDetY + rowHeight * 2, halfW - 24, rowHeight, false);

      drawCellText('Route:', left, tripDetY, true);
      drawCellText(reportData.tripName, left + 24, tripDetY, false);

      drawCellText('Start Time:', left, tripDetY + rowHeight, true);
      drawCellText('08:30 AM', left + 24, tripDetY + rowHeight, false);
      drawCellText('End Time:', left + halfW, tripDetY + rowHeight, true);
      drawCellText('12:15 PM', left + halfW + 24, tripDetY + rowHeight, false);

      drawCellText('Duration:', left, tripDetY + rowHeight * 2, true);
      drawCellText('3h 45m', left + 24, tripDetY + rowHeight * 2, false);
      drawCellText('Distance:', left + halfW, tripDetY + rowHeight * 2, true);
      drawCellText('148.6 km', left + halfW + 24, tripDetY + rowHeight * 2, false);

      doc.setDrawColor(203, 213, 225);
      doc.setLineWidth(0.3);
      doc.rect(left, tripDetY, contentWidth, tripDetH);
      doc.line(left, tripDetY + rowHeight, right, tripDetY + rowHeight);
      doc.line(left, tripDetY + rowHeight * 2, right, tripDetY + rowHeight * 2);
      doc.line(left + 24, tripDetY, left + 24, tripDetY + tripDetH);
      doc.line(left + halfW, tripDetY + rowHeight, left + halfW, tripDetY + tripDetH);
      doc.line(left + halfW + 24, tripDetY + rowHeight, left + halfW + 24, tripDetY + tripDetH);

      currentY += tripDetH;
    }

    /*
     * -------------------------------------------------------
     * ALERT SUMMARY
     * -------------------------------------------------------
     */
    currentY += 8;
    drawSectionHeader('Alert Summary', currentY);
    currentY += 5;

    const alertSumY = currentY;
    const alertSumH = rowHeight * 3;

    fillCell(left, alertSumY, 28, rowHeight, true);
    fillCell(left + 28, alertSumY, halfW - 28, rowHeight, false);
    fillCell(left + halfW, alertSumY, 28, rowHeight, true);
    fillCell(left + halfW + 28, alertSumY, halfW - 28, rowHeight, false);

    fillCell(left, alertSumY + rowHeight, 28, rowHeight, true);
    fillCell(left + 28, alertSumY + rowHeight, halfW - 28, rowHeight, false);
    fillCell(left + halfW, alertSumY + rowHeight, 30, rowHeight, true);
    fillCell(left + halfW + 30, alertSumY + rowHeight, halfW - 30, rowHeight, false);

    fillCell(left, alertSumY + rowHeight * 2, 28, rowHeight, true);
    fillCell(left + 28, alertSumY + rowHeight * 2, halfW - 28, rowHeight, false);
    fillCell(left + halfW, alertSumY + rowHeight * 2, 28, rowHeight, true);
    fillCell(left + halfW + 28, alertSumY + rowHeight * 2, halfW - 28, rowHeight, false);

    drawCellText('Total Alerts:', left, alertSumY, true);
    drawCellText(reportData.demoData.totalAlerts, left + 28, alertSumY, false);
    drawCellText('Overspeed:', left + halfW, alertSumY, true);
    drawCellText(reportData.demoData.overspeed, left + halfW + 28, alertSumY, false);

    drawCellText('Harsh Braking:', left, alertSumY + rowHeight, true);
    drawCellText(reportData.demoData.harshBraking, left + 28, alertSumY + rowHeight, false);
    drawCellText('GPS Disconnect:', left + halfW, alertSumY + rowHeight, true);
    drawCellText(reportData.demoData.gpsDisconnect, left + halfW + 30, alertSumY + rowHeight, false);

    drawCellText('Ignition:', left, alertSumY + rowHeight * 2, true);
    drawCellText(reportData.demoData.ignition, left + 28, alertSumY + rowHeight * 2, false);
    drawCellText('Night Driving:', left + halfW, alertSumY + rowHeight * 2, true);
    drawCellText(reportData.demoData.nightDriving, left + halfW + 28, alertSumY + rowHeight * 2, false);

    doc.setDrawColor(203, 213, 225);
    doc.setLineWidth(0.3);
    doc.rect(left, alertSumY, contentWidth, alertSumH);
    doc.line(left, alertSumY + rowHeight, right, alertSumY + rowHeight);
    doc.line(left, alertSumY + rowHeight * 2, right, alertSumY + rowHeight * 2);
    doc.line(left + halfW, alertSumY, left + halfW, alertSumY + alertSumH);
    doc.line(left + 28, alertSumY, left + 28, alertSumY + alertSumH);
    doc.line(left + halfW + 28, alertSumY, left + halfW + 28, alertSumY + rowHeight);
    doc.line(left + halfW + 30, alertSumY + rowHeight, left + halfW + 30, alertSumY + rowHeight * 2);
    doc.line(left + halfW + 28, alertSumY + rowHeight * 2, left + halfW + 28, alertSumY + alertSumH);

    currentY += alertSumH;

    /*
     * -------------------------------------------------------
     * RECENT ALERTS TABLE
     * -------------------------------------------------------
     */
    currentY += 8;
    drawSectionHeader('Recent Alerts', currentY);
    currentY += 5;

    const alertTableY = currentY;
    const alertCols = [32, 44, 64, 38];
    const alertHeaders = ['Time', 'Alert Type', 'Location', 'Speed'];
    const totalAlertRows = 1 + reportData.alerts.length;
    const alertTableH = totalAlertRows * rowHeight;

    doc.setFillColor(241, 245, 249);
    doc.rect(left, alertTableY, contentWidth, rowHeight, 'F');

    let curX = left;
    alertHeaders.forEach((hdr, idx) => {
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(9);
      doc.setTextColor(51, 65, 85);
      doc.text(hdr, curX + 3.5, alertTableY + 5.1);
      curX += alertCols[idx];
    });

    reportData.alerts.forEach((alertRow, rIdx) => {
      const rowY = alertTableY + rowHeight * (rIdx + 1);
      let rX = left;
      alertRow.forEach((cellVal, cIdx) => {
        doc.setFont('helvetica', 'normal');
        doc.setFontSize(9);
        doc.setTextColor(15, 23, 42);
        doc.text(String(cellVal), rX + 3.5, rowY + 5.1);
        rX += alertCols[cIdx];
      });
    });

    doc.setDrawColor(203, 213, 225);
    doc.setLineWidth(0.3);
    doc.rect(left, alertTableY, contentWidth, alertTableH);
    for (let r = 1; r < totalAlertRows; r++) {
      doc.line(left, alertTableY + rowHeight * r, right, alertTableY + rowHeight * r);
    }
    let colDividerX = left;
    for (let c = 0; c < alertCols.length - 1; c++) {
      colDividerX += alertCols[c];
      doc.line(colDividerX, alertTableY, colDividerX, alertTableY + alertTableH);
    }

    /*
     * -------------------------------------------------------
     * FOOTER
     * -------------------------------------------------------
     */
    doc.setDrawColor(203, 213, 225);
    doc.setLineWidth(0.4);
    doc.line(left, 280, right, 280);

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(8.5);
    doc.setTextColor(100, 116, 139);
    doc.text('TrackFleet', left, 286);
    doc.text(`Generated ${reportData.generatedDate}`, right, 286, { align: 'right' });

    /*
     * -------------------------------------------------------
     * SAVE
     * -------------------------------------------------------
     */
    const safeReportType = reportData.reportType.replace(/\s+/g, '-');
    doc.save(`TrackFleet-${safeReportType}-Report.pdf`);
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
    setShowPreview(false);
    setReportPreview(null);
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
              onClick={handleGenerateReport}
            >
              Generate Report
            </button>

          </div>

        </div>

      </div>

      {/* GENERATED REPORT PREVIEW */}

      {showPreview && reportPreview && (
        <div ref={previewRef} className="mt-5 mb-5">

          {/* PREVIEW TOOLBAR */}
          <div className="d-flex flex-wrap justify-content-between align-items-center bg-white border rounded-4 p-3 px-4 mb-4 shadow-sm">
            <div>
              <div className="d-flex align-items-center gap-2 mb-1">
                <span className="badge bg-primary-subtle text-primary border border-primary-subtle px-3 py-2 fw-semibold">
                  Report Preview
                </span>
                <span className="fw-bold text-dark fs-5">
                  {reportPreview.reportType} Report
                </span>
              </div>
              <p className="text-muted small mb-0">
                Review the report below. Click &quot;Download PDF&quot; when you are ready to save a copy.
              </p>
            </div>

            <button
              type="button"
              className="btn btn-primary d-inline-flex align-items-center gap-2 px-4 py-2 fw-semibold shadow-sm mt-3 mt-sm-0"
              onClick={() => downloadPDF(reportPreview)}
            >
              <Download size={18} />
              Download PDF
            </button>
          </div>

          {/* REPORT DOCUMENT PAPER */}
          <div
            className="bg-white border rounded-4 p-4 p-md-5 shadow-sm mx-auto"
            style={{
              maxWidth: '920px',
              color: '#0f172a',
              backgroundColor: '#ffffff'
            }}
          >

            {/* HEADER */}
            <div
              className="d-flex justify-content-between align-items-start pb-3 border-bottom mb-4"
              style={{ borderColor: '#cbd5e1' }}
            >
              <div>
                <h2 className="fw-bold mb-1" style={{ color: '#1e293b', letterSpacing: '-0.5px' }}>
                  TrackFleet
                </h2>
                <p className="text-secondary small mb-0">
                  Fleet Management System
                </p>
              </div>
              <div className="text-end">
                <span
                  className="badge px-3 py-2 fw-semibold"
                  style={{ backgroundColor: '#f1f5f9', color: '#475569', border: '1px solid #cbd5e1' }}
                >
                  Official Report
                </span>
              </div>
            </div>

            {/* REPORT TITLE */}
            <div className="mb-4">
              <h4 className="fw-bold mb-0" style={{ color: '#0f172a' }}>
                {reportPreview.reportType} Report
              </h4>
            </div>

            {/* BASIC INFORMATION */}
            <div className="table-responsive mb-4">
              <table
                className="table table-bordered mb-0 align-middle"
                style={{ borderColor: '#cbd5e1', fontSize: '0.9rem' }}
              >
                <tbody>
                  <tr>
                    <td className="fw-bold py-2.5 px-3" style={{ width: '20%', backgroundColor: '#f8fafc', color: '#475569' }}>
                      Driver:
                    </td>
                    <td className="py-2.5 px-3" style={{ width: '30%', color: '#0f172a' }}>
                      {reportPreview.driverName}
                    </td>
                    <td className="fw-bold py-2.5 px-3" style={{ width: '20%', backgroundColor: '#f8fafc', color: '#475569' }}>
                      Vehicle:
                    </td>
                    <td className="py-2.5 px-3" style={{ width: '30%', color: '#0f172a' }}>
                      {reportPreview.vehicleNumber}
                    </td>
                  </tr>
                  <tr>
                    <td className="fw-bold py-2.5 px-3" style={{ backgroundColor: '#f8fafc', color: '#475569' }}>
                      Reporting Period:
                    </td>
                    <td className="py-2.5 px-3" style={{ color: '#0f172a' }}>
                      {reportPreview.period}
                    </td>
                    <td className="fw-bold py-2.5 px-3" style={{ backgroundColor: '#f8fafc', color: '#475569' }}>
                      Generated:
                    </td>
                    <td className="py-2.5 px-3" style={{ color: '#0f172a' }}>
                      {reportPreview.generatedDate}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            {/* PERFORMANCE SUMMARY */}
            <div className="mb-4">
              <h5 className="fw-bold mb-2" style={{ color: '#0f172a', fontSize: '1.05rem' }}>
                Performance Summary
              </h5>
              <div className="border-bottom mb-3" style={{ borderColor: '#e2e8f0' }}></div>
              <div className="table-responsive">
                <table
                  className="table table-bordered mb-0 align-middle"
                  style={{ borderColor: '#cbd5e1', fontSize: '0.9rem' }}
                >
                  <tbody>
                    <tr>
                      <td className="py-2.5 px-3" style={{ width: '33.33%' }}>
                        <span className="fw-bold me-2" style={{ color: '#475569' }}>Distance:</span>
                        <span style={{ color: '#0f172a' }}>{reportPreview.demoData.distance}</span>
                      </td>
                      <td className="py-2.5 px-3" style={{ width: '33.33%' }}>
                        <span className="fw-bold me-2" style={{ color: '#475569' }}>Driving Time:</span>
                        <span style={{ color: '#0f172a' }}>{reportPreview.demoData.drivingTime}</span>
                      </td>
                      <td className="py-2.5 px-3" style={{ width: '33.34%' }}>
                        <span className="fw-bold me-2" style={{ color: '#475569' }}>Avg. Speed:</span>
                        <span style={{ color: '#0f172a' }}>{reportPreview.demoData.averageSpeed}</span>
                      </td>
                    </tr>
                    <tr>
                      <td className="py-2.5 px-3">
                        <span className="fw-bold me-2" style={{ color: '#475569' }}>Max Speed:</span>
                        <span style={{ color: '#0f172a' }}>{reportPreview.demoData.maximumSpeed}</span>
                      </td>
                      <td className="py-2.5 px-3" colSpan={2}>
                        <span className="fw-bold me-2" style={{ color: '#475569' }}>Performance Score:</span>
                        <span style={{ color: '#0f172a' }}>{reportPreview.demoData.performanceScore}</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            {/* TRIP SUMMARY */}
            <div className="mb-4">
              <h5 className="fw-bold mb-2" style={{ color: '#0f172a', fontSize: '1.05rem' }}>
                Trip Summary
              </h5>
              <div className="border-bottom mb-3" style={{ borderColor: '#e2e8f0' }}></div>
              <div className="table-responsive">
                <table
                  className="table table-bordered mb-0 align-middle"
                  style={{ borderColor: '#cbd5e1', fontSize: '0.9rem' }}
                >
                  <tbody>
                    <tr>
                      <td className="py-2.5 px-3" style={{ width: '50%' }}>
                        <span className="fw-bold me-2" style={{ color: '#475569' }}>Trips Completed:</span>
                        <span style={{ color: '#0f172a' }}>{reportPreview.demoData.totalTrips}</span>
                      </td>
                      <td className="py-2.5 px-3" style={{ width: '50%' }}>
                        <span className="fw-bold me-2" style={{ color: '#475569' }}>Total Distance:</span>
                        <span style={{ color: '#0f172a' }}>{reportPreview.demoData.distance}</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            {/* TRIP DETAILS (if reportType === 'Trip') */}
            {reportPreview.reportType === 'Trip' && (
              <div className="mb-4">
                <h5 className="fw-bold mb-2" style={{ color: '#0f172a', fontSize: '1.05rem' }}>
                  Trip Details
                </h5>
                <div className="border-bottom mb-3" style={{ borderColor: '#e2e8f0' }}></div>
                <div className="table-responsive">
                  <table
                    className="table table-bordered mb-0 align-middle"
                    style={{ borderColor: '#cbd5e1', fontSize: '0.9rem' }}
                  >
                    <tbody>
                      <tr>
                        <td className="py-2.5 px-3" colSpan={2}>
                          <span className="fw-bold me-2" style={{ color: '#475569' }}>Route:</span>
                          <span style={{ color: '#0f172a' }}>{reportPreview.tripName}</span>
                        </td>
                      </tr>
                      <tr>
                        <td className="py-2.5 px-3" style={{ width: '50%' }}>
                          <span className="fw-bold me-2" style={{ color: '#475569' }}>Start Time:</span>
                          <span style={{ color: '#0f172a' }}>08:30 AM</span>
                        </td>
                        <td className="py-2.5 px-3" style={{ width: '50%' }}>
                          <span className="fw-bold me-2" style={{ color: '#475569' }}>End Time:</span>
                          <span style={{ color: '#0f172a' }}>12:15 PM</span>
                        </td>
                      </tr>
                      <tr>
                        <td className="py-2.5 px-3">
                          <span className="fw-bold me-2" style={{ color: '#475569' }}>Duration:</span>
                          <span style={{ color: '#0f172a' }}>3h 45m</span>
                        </td>
                        <td className="py-2.5 px-3">
                          <span className="fw-bold me-2" style={{ color: '#475569' }}>Distance:</span>
                          <span style={{ color: '#0f172a' }}>148.6 km</span>
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {/* ALERT SUMMARY */}
            <div className="mb-4">
              <h5 className="fw-bold mb-2" style={{ color: '#0f172a', fontSize: '1.05rem' }}>
                Alert Summary
              </h5>
              <div className="border-bottom mb-3" style={{ borderColor: '#e2e8f0' }}></div>
              <div className="table-responsive">
                <table
                  className="table table-bordered mb-0 align-middle"
                  style={{ borderColor: '#cbd5e1', fontSize: '0.9rem' }}
                >
                  <tbody>
                    <tr>
                      <td className="py-2.5 px-3" style={{ width: '50%' }}>
                        <span className="fw-bold me-2" style={{ color: '#475569' }}>Total Alerts:</span>
                        <span style={{ color: '#0f172a' }}>{reportPreview.demoData.totalAlerts}</span>
                      </td>
                      <td className="py-2.5 px-3" style={{ width: '50%' }}>
                        <span className="fw-bold me-2" style={{ color: '#475569' }}>Overspeed:</span>
                        <span style={{ color: '#0f172a' }}>{reportPreview.demoData.overspeed}</span>
                      </td>
                    </tr>
                    <tr>
                      <td className="py-2.5 px-3">
                        <span className="fw-bold me-2" style={{ color: '#475569' }}>Harsh Braking:</span>
                        <span style={{ color: '#0f172a' }}>{reportPreview.demoData.harshBraking}</span>
                      </td>
                      <td className="py-2.5 px-3">
                        <span className="fw-bold me-2" style={{ color: '#475569' }}>GPS Disconnect:</span>
                        <span style={{ color: '#0f172a' }}>{reportPreview.demoData.gpsDisconnect}</span>
                      </td>
                    </tr>
                    <tr>
                      <td className="py-2.5 px-3">
                        <span className="fw-bold me-2" style={{ color: '#475569' }}>Ignition:</span>
                        <span style={{ color: '#0f172a' }}>{reportPreview.demoData.ignition}</span>
                      </td>
                      <td className="py-2.5 px-3">
                        <span className="fw-bold me-2" style={{ color: '#475569' }}>Night Driving:</span>
                        <span style={{ color: '#0f172a' }}>{reportPreview.demoData.nightDriving}</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            {/* RECENT ALERTS */}
            <div className="mb-4">
              <h5 className="fw-bold mb-2" style={{ color: '#0f172a', fontSize: '1.05rem' }}>
                Recent Alerts
              </h5>
              <div className="border-bottom mb-3" style={{ borderColor: '#e2e8f0' }}></div>
              <div className="table-responsive">
                <table
                  className="table table-bordered mb-0 align-middle"
                  style={{ borderColor: '#cbd5e1', fontSize: '0.9rem' }}
                >
                  <thead style={{ backgroundColor: '#f1f5f9' }}>
                    <tr>
                      <th
                        className="fw-bold py-2.5 px-3 border"
                        style={{ width: '20%', color: '#334155', backgroundColor: '#f1f5f9' }}
                      >
                        Time
                      </th>
                      <th
                        className="fw-bold py-2.5 px-3 border"
                        style={{ width: '25%', color: '#334155', backgroundColor: '#f1f5f9' }}
                      >
                        Alert Type
                      </th>
                      <th
                        className="fw-bold py-2.5 px-3 border"
                        style={{ width: '35%', color: '#334155', backgroundColor: '#f1f5f9' }}
                      >
                        Location
                      </th>
                      <th
                        className="fw-bold py-2.5 px-3 border"
                        style={{ width: '20%', color: '#334155', backgroundColor: '#f1f5f9' }}
                      >
                        Speed
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {reportPreview.alerts.map((alert, idx) => (
                      <tr key={idx}>
                        <td className="py-2.5 px-3 border" style={{ color: '#0f172a' }}>
                          {alert[0]}
                        </td>
                        <td className="py-2.5 px-3 border" style={{ color: '#0f172a' }}>
                          {alert[1]}
                        </td>
                        <td className="py-2.5 px-3 border" style={{ color: '#0f172a' }}>
                          {alert[2]}
                        </td>
                        <td className="py-2.5 px-3 border" style={{ color: '#0f172a' }}>
                          {alert[3]}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* FOOTER */}
            <div
              className="d-flex justify-content-between align-items-center pt-3 border-top mt-4"
              style={{ borderColor: '#cbd5e1', color: '#64748b', fontSize: '0.85rem' }}
            >
              <span>TrackFleet</span>
              <span>Generated {reportPreview.generatedDate}</span>
            </div>

          </div>

          {/* BOTTOM DOWNLOAD ACTION */}
          <div className="d-flex justify-content-center mt-4">
            <button
              type="button"
              className="btn btn-primary d-inline-flex align-items-center gap-2 px-4 py-2 fw-semibold shadow-sm"
              onClick={() => downloadPDF(reportPreview)}
            >
              <Download size={18} />
              Download PDF
            </button>
          </div>

        </div>
      )}

    </div>
  );
};

export default Reports;