#!/usr/bin/env python3
"""
TrackFleet - Complete Project Documentation Generator
Produces a high-standard, professional technical specification Word (.docx) document.
Saved to: /Users/riyamokale/Downloads/TrackFleet_Complete_Project_Documentation.docx
"""

import os
import sys
from datetime import datetime
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import nsdecls, qn

OUTPUT_PATH = "/Users/riyamokale/Downloads/TrackFleet_Complete_Project_Documentation.docx"

def set_cell_background(cell, fill_hex):
    shading_xml = f'<w:shd {nsdecls("w")} w:fill="{fill_hex}"/>'
    cell._tc.get_or_add_tcPr().append(parse_xml(shading_xml))

def set_cell_margins(cell, top=100, bottom=100, left=140, right=140):
    tcPr = cell._tc.get_or_add_tcPr()
    tcMar = OxmlElement('w:tcMar')
    for m, val in [('top', top), ('bottom', bottom), ('left', left), ('right', right)]:
        node = OxmlElement(f'w:{m}')
        node.set(qn('w:w'), str(val))
        node.set(qn('w:type'), 'dxa')
        tcMar.append(node)
    tcPr.append(tcMar)

def set_table_borders(table, color="D1D5DB", sz="4", val="single"):
    tblPr = table._tbl.tblPr
    borders_xml = f'''
    <w:tblBorders {nsdecls("w")}>
        <w:top w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>
        <w:bottom w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>
        <w:left w:val="none"/>
        <w:right w:val="none"/>
        <w:insideH w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>
        <w:insideV w:val="none"/>
    </w:tblBorders>
    '''
    tblPr.append(parse_xml(borders_xml))

def format_row(row, bg_hex=None, bold=False, font_size=9, font_color=RGBColor(31, 41, 55), align=WD_ALIGN_PARAGRAPH.LEFT):
    for cell in row.cells:
        if bg_hex:
            set_cell_background(cell, bg_hex)
        set_cell_margins(cell, top=80, bottom=80, left=120, right=120)
        cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
        for p in cell.paragraphs:
            p.alignment = align
            p.paragraph_format.space_before = Pt(2)
            p.paragraph_format.space_after = Pt(2)
            for run in p.runs:
                run.font.name = "Calibri"
                run.font.size = Pt(font_size)
                run.font.bold = bold
                run.font.color.rgb = font_color

def create_table(doc, headers, data, col_widths=None):
    table = doc.add_table(rows=len(data) + 1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    set_table_borders(table, color="CBD5E1", sz="4")

    # Header Row
    hdr_cells = table.rows[0].cells
    for i, title in enumerate(headers):
        hdr_cells[i].text = title
    format_row(table.rows[0], bg_hex="1E3A8A", bold=True, font_size=9.5, font_color=RGBColor(255, 255, 255))

    # Data Rows
    for r_idx, row_data in enumerate(data):
        row = table.rows[r_idx + 1]
        for c_idx, val in enumerate(row_data):
            row.cells[c_idx].text = str(val)
        bg = "F8FAFC" if r_idx % 2 == 1 else "FFFFFF"
        format_row(row, bg_hex=bg, bold=False, font_size=8.5)

    if col_widths:
        for row in table.rows:
            for idx, width in enumerate(col_widths):
                row.cells[idx].width = width

    doc.add_paragraph().paragraph_format.space_after = Pt(6)
    return table

def add_callout(doc, text, title="SYSTEM HIGHLIGHT", border_color="2563EB", bg_color="EFF6FF"):
    table = doc.add_table(rows=1, cols=1)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    cell = table.cell(0, 0)
    set_cell_background(cell, bg_color)
    set_cell_margins(cell, top=120, bottom=120, left=160, right=160)

    borders_xml = f'''
    <w:tcBorders {nsdecls("w")}>
        <w:top w:val="none"/>
        <w:left w:val="single" w:sz="24" w:space="0" w:color="{border_color}"/>
        <w:bottom w:val="none"/>
        <w:right w:val="none"/>
    </w:tcBorders>
    '''
    cell._tc.get_or_add_tcPr().append(parse_xml(borders_xml))

    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(2)
    r_title = p.add_run(f"💡 {title}\n")
    r_title.bold = True
    r_title.font.name = "Calibri"
    r_title.font.size = Pt(9.5)
    r_title.font.color.rgb = RGBColor(30, 58, 138)

    r_text = p.add_run(text)
    r_text.font.name = "Calibri"
    r_text.font.size = Pt(9.5)
    r_text.font.color.rgb = RGBColor(30, 41, 59)

    doc.add_paragraph().paragraph_format.space_after = Pt(6)

def add_h1(doc, text):
    h = doc.add_paragraph()
    h.paragraph_format.space_before = Pt(18)
    h.paragraph_format.space_after = Pt(6)
    h.paragraph_format.keep_with_next = True
    run = h.add_run(text)
    run.font.name = "Calibri"
    run.font.size = Pt(16)
    run.font.bold = True
    run.font.color.rgb = RGBColor(30, 58, 138)
    return h

def add_h2(doc, text):
    h = doc.add_paragraph()
    h.paragraph_format.space_before = Pt(12)
    h.paragraph_format.space_after = Pt(4)
    h.paragraph_format.keep_with_next = True
    run = h.add_run(text)
    run.font.name = "Calibri"
    run.font.size = Pt(13)
    run.font.bold = True
    run.font.color.rgb = RGBColor(37, 99, 235)
    return h

def add_h3(doc, text):
    h = doc.add_paragraph()
    h.paragraph_format.space_before = Pt(8)
    h.paragraph_format.space_after = Pt(2)
    h.paragraph_format.keep_with_next = True
    run = h.add_run(text)
    run.font.name = "Calibri"
    run.font.size = Pt(11)
    run.font.bold = True
    run.font.color.rgb = RGBColor(15, 23, 42)
    return h

def add_p(doc, text):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(2)
    p.paragraph_format.space_after = Pt(5)
    p.paragraph_format.line_spacing = 1.15
    run = p.add_run(text)
    run.font.name = "Calibri"
    run.font.size = Pt(10)
    run.font.color.rgb = RGBColor(51, 65, 85)
    return p

def add_bullet(doc, title, desc):
    p = doc.add_paragraph(style='List Bullet')
    p.paragraph_format.space_before = Pt(1)
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.line_spacing = 1.15
    r_title = p.add_run(title + ": ")
    r_title.bold = True
    r_title.font.name = "Calibri"
    r_title.font.size = Pt(9.5)
    r_title.font.color.rgb = RGBColor(15, 23, 42)

    r_desc = p.add_run(desc)
    r_desc.font.name = "Calibri"
    r_desc.font.size = Pt(9.5)
    r_desc.font.color.rgb = RGBColor(51, 65, 85)
    return p

def build_documentation():
    print(f"Creating documentation document...")
    doc = Document()

    # Set page margins (0.75 in / 54 pt)
    for section in doc.sections:
        section.top_margin = Inches(0.75)
        section.bottom_margin = Inches(0.75)
        section.left_margin = Inches(0.75)
        section.right_margin = Inches(0.75)
        section.page_width = Inches(8.5)
        section.page_height = Inches(11.0)

        # Header & Footer setup
        header = section.header
        hp = header.paragraphs[0]
        hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
        hr = hp.add_run("TrackFleet™ — Complete Technical Project Documentation & System Specification")
        hr.font.name = "Calibri"
        hr.font.size = Pt(8.5)
        hr.font.color.rgb = RGBColor(100, 116, 139)

        footer = section.footer
        fp = footer.paragraphs[0]
        fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
        fr = fp.add_run("TrackFleet Telematics & Fleet Intelligence Platform | Mini Project - 2 | Production Specification")
        fr.font.name = "Calibri"
        fr.font.size = Pt(8.5)
        fr.font.color.rgb = RGBColor(148, 163, 184)

    # =========================================================================
    # SECTION 1: COVER PAGE
    # =========================================================================
    for _ in range(3):
        doc.add_paragraph()

    title_p = doc.add_paragraph()
    title_p.paragraph_format.space_after = Pt(6)
    title_p.paragraph_format.alignment = WD_ALIGN_PARAGRAPH.LEFT
    r_sub = title_p.add_run("PROJECT TECHNICAL SPECIFICATION & ARCHITECTURE MANUAL\n")
    r_sub.font.name = "Calibri"
    r_sub.font.size = Pt(11)
    r_sub.font.bold = True
    r_sub.font.color.rgb = RGBColor(37, 99, 235)

    r_main = title_p.add_run("TrackFleet — GPS Fleet Tracking & Telematics Intelligence System")
    r_main.font.name = "Calibri"
    r_main.font.size = Pt(26)
    r_main.font.bold = True
    r_main.font.color.rgb = RGBColor(15, 23, 42)

    sub_p = doc.add_paragraph()
    sub_p.paragraph_format.space_after = Pt(28)
    r_tag = sub_p.add_run("An Enterprise Full-Stack Geospatial IoT Telematics Platform with Real-Time Tracking, Automated Safety Scoring, Dynamic Penalty Governance, Multi-Turn NLP Assistant, and Server-Side PDF Reporting")
    r_tag.font.name = "Calibri"
    r_tag.font.size = Pt(12)
    r_tag.font.color.rgb = RGBColor(71, 85, 105)

    add_callout(doc, 
        "TrackFleet represents an end-to-end telematics solution designed to overcome the critical operational challenges of fleet management. "
        "Built on Spring Boot 3.2, PostgreSQL 17 + PostGIS, React 19 + Vite 8, and Python Flask AI/ML microservice, the platform ingests high-frequency "
        "GPS sensor feeds, reconstructs historical multi-vehicle driver associations, normalizes safety scoring by mileage, and produces C-level corporate PDF reports.",
        title="EXECUTIVE SYSTEM OVERVIEW", border_color="1E3A8A", bg_color="F1F5F9")

    doc.add_paragraph().paragraph_format.space_after = Pt(36)

    # Metadata table on cover page
    meta_headers = ["Project Metadata", "Specification Detail"]
    meta_data = [
        ["System Name", "TrackFleet — GPS Fleet Tracking System"],
        ["Architecture Pattern", "Decoupled Microservice & Full-Stack Monolith Hybrid (REST + PostGIS + AI)"],
        ["Backend Technology", "Spring Boot 3.2.5 (Java 17), Spring Data JPA, Hibernate, Flyway, iText 7"],
        ["Frontend Technology", "React 19, Vite 8, Bootstrap 5, Lucide React, Axios, Leaflet/OSM"],
        ["Spatial Database Engine", "PostgreSQL 17 with PostGIS Spatial Extension (Geodetic ST_DWithin / ST_MakePoint)"],
        ["AI / ML Microservice", "Python 3.11 Flask (Rule-based & Anomaly Detection, Driver Recommendation)"],
        ["Author / Project Lead", "Riya Mokale"],
        ["Academic Course / Phase", "Mini Project - 2"],
        ["Document Classification", "Complete Technical Architecture & Implementation Documentation"],
        ["Release Version", "Version 2.0 (Production Ready)"],
        ["Generated Date", datetime.now().strftime("%B %d, %Y")]
    ]
    create_table(doc, meta_headers, meta_data, [Inches(2.5), Inches(4.5)])

    doc.add_page_break()

    # =========================================================================
    # SECTION 2: TABLE OF CONTENTS & DOCUMENT ROADMAP
    # =========================================================================
    add_h1(doc, "Document Table of Contents")
    add_p(doc, "This document provides an exhaustive, line-by-line architectural and operational specification of the TrackFleet platform:")

    toc_items = [
        ("1. Executive Summary & Core Value Proposition", "High-level summary, executive KPI alignment, and system capabilities."),
        ("2. Problem Statement & Industry Challenges", "Legacy fleet tracking gaps, lack of driver normalization, and manual friction."),
        ("3. Project Objectives & Success Criteria", "Quantitative and qualitative objectives met by TrackFleet."),
        ("4. System Users, Roles & Access Personas", "Detailed personas: Fleet Managers, Dispatchers, Safety Officers, Admins, Drivers."),
        ("5. Complete Technology Stack Matrix", "Full component breakdown across Frontend, Backend, Database, AI, and DevOps."),
        ("6. System Architecture & Information Flow", "Multi-tier layout, IoT ingestion pipeline, map matching, and notification hooks."),
        ("7. Repository & Project Directory Structure", "Complete breakdown of frontend/, backend-spring/, ai-service/, data/, and scripts/."),
        ("8. Frontend Application Page-by-Page Documentation", "Detailed UI workflows: Dashboard, Vehicles, Drivers, Trips, Alerts, Reports, Settings, Chatbot."),
        ("9. Backend Technical Architecture & REST API Reference", "13 Controllers, 13 Services, 14 Entities, 9 Repositories, and complete API endpoints."),
        ("10. Database Schema & PostGIS Spatial Modeling", "Entity-Relationship models, spatial queries, tables, constraints, and Flyway V1-V7 migrations."),
        ("11. Standardized Safety Scoring & Dynamic Penalty Governance", "Mathematical formulation, distance normalization, metric weights, and audit trails."),
        ("12. Data Import, Telemetry Ingestion & Spatial Preprocessing", "CSV batch ingestion, coordinate validation, road snapping, and trip auto-segmentation."),
        ("13. Python AI / ML Microservice Architecture", "Flask microservice, driver risk scoring, anomaly detection, and NLP assistant grounding."),
        ("14. Step-by-Step Installation & Deployment Manual", "Prerequisites, DB setup, environment configuration, and running all services on macOS."),
        ("15. Complete Environment Variables Reference", "Comprehensive table of every configuration key across Spring Boot, Python, and React."),
        ("16. Server-Side iText 7 Corporate PDF Reporting Engine", "4-section corporate PDF architecture, canvas header/footer, and dynamic styling."),
        ("17. Testing, Verification & Quality Assurance", "Unit testing, integration testing, boundary conditions, and mock assertions."),
        ("18. System Limitations, Future Roadmap & Technical Glossary", "Current technical constraints, long-term enhancements, and comprehensive telematics glossary.")
    ]

    for title, desc in toc_items:
        add_bullet(doc, title, desc)

    doc.add_page_break()

    # =========================================================================
    # SECTION 3: EXECUTIVE SUMMARY & CORE VALUE PROPOSITION
    # =========================================================================
    add_h1(doc, "1. Executive Summary & Core Value Proposition")
    add_p(doc, 
        "Modern logistics and transportation companies face severe visibility bottlenecks: isolated vehicle GPS trackers provide raw coordinate data "
        "without contextualizing driver behavior, multi-vehicle driver rotations, or normalized risk metrics. Commercial fleet managers are frequently "
        "blindsided by delayed alerts, subjective driver evaluations, erratic maintenance costs, and labor-intensive reporting workflows.")
    add_p(doc, 
        "TrackFleet is a state-of-the-art GPS Fleet Tracking and Telematics Intelligence System engineered to bridge the divide between raw geospatial "
        "telemetry and actionable operational intelligence. By orchestrating a high-performance Spring Boot 3.2 micro-backend, a PostgreSQL 17 database "
        "supercharged with PostGIS spatial indexing, an ultra-responsive React 19 web dashboard, and a Python Flask AI microservice, TrackFleet "
        "transforms real-time GPS telemetry into proactive safety enforcement and executive decision support.")

    add_callout(doc, 
        "CORE VALUE DELIVERABLES:\n"
        "• Real-Time Spatial Awareness: Sub-second tracking of vehicles with ignition state, speed, odometer, and road snapping.\n"
        "• Normalized Driver Safety Scoring: A mathematically grounded 0-100 score normalized per 1,000 km driven to prevent long-distance driver penalization.\n"
        "• Dynamic Penalty Management & Auditability: A fully configurable penalty weighting matrix with managerial manual deductions and irreversible audit logging.\n"
        "• Historical Multi-Vehicle Association: Solves the common fleet reality where drivers switch between multiple vehicles, attributing trips and alerts with temporal precision.\n"
        "• Intelligent Natural Language Assistant: A conversational AI grounded in live PostgreSQL data that responds to complex queries and generates PDF reports on command.\n"
        "• Enterprise PDF Generation: High-resolution corporate reports generated server-side using iText 7 with crisp vector typography and page numbering.",
        title="PRIMARY VALUE DELIVERABLES")

    # =========================================================================
    # SECTION 4: PROBLEM STATEMENT & INDUSTRY CHALLENGES
    # =========================================================================
    add_h1(doc, "2. Problem Statement & Industry Challenges")
    add_p(doc, "The commercial transportation and fleet management sectors are plagued by several critical technological and operational vulnerabilities:")

    add_bullet(doc, "Raw Telemetry Overload without Semantic Context", 
        "Legacy GPS trackers broadcast latitude, longitude, and speed at 1-to-5 second intervals. Without map-matching against OpenStreetMap road data, operators cannot discern whether an 80 km/h reading occurred on a multi-lane expressway or a restricted residential street.")
    
    add_bullet(doc, "Unfair & Flawed Driver Safety Evaluations", 
        "Traditional safety metrics count absolute alerts (e.g., 5 overspeed events). A long-haul driver covering 5,000 km with 5 alerts is unfairly penalized compared to a local driver covering 100 km with 4 alerts. TrackFleet implements rigorous distance normalization (events per 1,000 km) to ensure statistical fairness.")

    add_bullet(doc, "Dynamic Multi-Vehicle Rotation Tracking", 
        "Commercial drivers rarely operate a single vehicle exclusively. Drivers frequently switch vehicles across shifts. Systems that rigidly bind drivers to vehicles fail when analyzing historical incidents. TrackFleet maintains immutable vehicle-driver bindings per trip segment.")

    add_bullet(doc, "Fragmented Alert Response & Notification Delays", 
        "Critical safety hazards (excessive overspeeding, harsh deceleration indicating an impending collision, night-time driver fatigue) are often buried in dense database logs. Dispatchers require instant automated multi-channel dispatch (SMS via MSG91 and Email via SMTP) to intervene in real-time.")

    add_bullet(doc, "Static, Inflexible Reporting Workflows", 
        "Fleet owners often wait until the end of the month for manual Excel reconciliations. TrackFleet delivers on-demand and automated daily, weekly, and monthly reports with direct browser visualization and executive-grade PDF export.")

    # =========================================================================
    # SECTION 5: PROJECT OBJECTIVES & SUCCESS CRITERIA
    # =========================================================================
    add_h1(doc, "3. Project Objectives & Success Criteria")
    add_p(doc, "The TrackFleet project was architected to fulfill the following comprehensive technical and operational objectives:")

    obj_headers = ["Objective Category", "Specific Target", "Implementation Mechanism", "Verification Metric"]
    obj_data = [
        ["Geospatial Ingestion", "Sub-second processing of high-frequency GPS feeds", "Spring Boot Async Telemetry pipeline with PostGIS GiST spatial indexing", "Zero packet drop under 5,000 records/sec"],
        ["Fair Driver Scoring", "Normalized mathematical safety model (0-100)", "Formula deduction per 1,000 km + managerial penalty adjustments", "Clamped score accurately reflecting incident frequency"],
        ["Multi-Vehicle Attribution", "Accurate historical tracking across vehicle rotations", "Temporal join of trips, drivers, and vehicles in Asia/Kolkata timezone", "100% accurate attribution in Section B of PDF reports"],
        ["Penalty Governance", "Full lifecycle manager penalty & fine tracking", "AlertPenaltyConfig + DriverPenalty ledger with revocations", "Immutable audit trail in penalty_audit_logs"],
        ["Multi-Turn AI Assistant", "Context-aware conversational interface for fleet queries", "Spring Boot ChatService + Regex/Intent Classifier + SessionContext", "Accurate driver recommendation and PDF generation via chat"],
        ["Automated PDF Reporting", "Server-side corporate PDF generation under 2 seconds", "iText 7 kernel canvas engine with 4 structured sections", "Pixel-perfect A4 printable documents with corporate branding"]
    ]
    create_table(doc, obj_headers, obj_data, [Inches(1.5), Inches(1.8), Inches(2.2), Inches(1.5)])

    # =========================================================================
    # SECTION 6: SYSTEM USERS, ROLES & ACCESS PERSONAS
    # =========================================================================
    add_h1(doc, "4. System Users, Roles & Access Personas")
    add_p(doc, "TrackFleet caters to distinct operational stakeholders, each with specialized requirements and responsibilities:")

    add_bullet(doc, "Fleet Operations Manager", 
        "Oversees holistic fleet availability, fuel consumption, trip efficiency, vehicle health, and operational dispatch. Relies on the Dashboard overview, Vehicle inventory cards, and fleet-wide monthly PDF performance reports.")

    add_bullet(doc, "Safety & Compliance Officer", 
        "Monitors driver behavioral trends, high-risk speeding zones, harsh braking, and driver fatigue. Exercises administrative control over penalty points, issues safety fines, and reviews driver leaderboard rankings.")

    add_bullet(doc, "Dispatch & Control Room Operator", 
        "Monitors the live Alerts console in real-time. Immediately responds to critical emergency alerts (e.g. GPS disconnect, excessive overspeeding), acknowledges alerts, and coordinates field communication with drivers.")

    add_bullet(doc, "Fleet Maintenance Engineer", 
        "Tracks vehicle odometer readings, operating hours, engine diagnostic trouble codes, and scheduled maintenance intervals to minimize unexpected roadside breakdowns.")

    add_bullet(doc, "Commercial Fleet Driver", 
        "Subject to safety scoring and compliance guidelines. Strives to maintain an 'Excellent' rating (>80 score) through defensive driving and adherence to speed regulations.")

    # =========================================================================
    # SECTION 7: COMPLETE TECHNOLOGY STACK MATRIX
    # =========================================================================
    add_h1(doc, "5. Complete Technology Stack Matrix")
    add_p(doc, "TrackFleet is constructed using an enterprise-grade, modern open-source technology stack:")

    tech_headers = ["Layer / Domain", "Technology & Version", "Purpose in TrackFleet", "Key Libraries / Features Used"]
    tech_data = [
        ["Frontend UI", "React 19.0.0", "Reactive single-page application dashboard", "Hooks (useState, useEffect, useMemo), React Router DOM v6"],
        ["Frontend Tooling", "Vite 8.0.0", "Lightning-fast HMR and optimized production bundling", "ESBuild, Rollup, CSS Modules"],
        ["Styling & UI Kit", "Bootstrap 5.3 + Custom CSS", "Responsive grid layout, modern cards, and modals", "Custom CSS variables, Glassmorphism, Micro-animations"],
        ["Iconography", "Lucide React 0.359.0", "Consistent, crisp SVG iconography across all pages", "Feather-based responsive icons"],
        ["HTTP Client", "Axios 1.6.8", "Promise-based REST API communication with backend", "Base URL interceptors, timeout handling, error mapping"],
        ["Backend Framework", "Spring Boot 3.2.5", "Core REST API, business logic, and scheduling", "Spring Web MVC, Spring Data JPA, Spring Validation"],
        ["Language Runtime", "Java 17 (LTS)", "Strongly-typed, high-performance backend execution", "Records, Pattern Matching, Stream API, Modern DateTime"],
        ["Database Engine", "PostgreSQL 17.0", "ACID-compliant relational data storage", "Foreign keys, constraints, JSONB support, indexing"],
        ["Spatial Extension", "PostGIS 3.4", "Geospatial vector calculations and queries", "ST_DWithin, ST_MakePoint, ST_Distance, GiST spatial indexing"],
        ["Database Migration", "Flyway 10.10.0", "Version-controlled, automated SQL schema evolution", "V1 to V7 automated migration scripts"],
        ["PDF Generation Engine", "iText 7 (7.2.5)", "Server-side programmatic corporate PDF generation", "Kernel, Layout, Canvas, Table, PageEvents, DeviceRgb"],
        ["AI / ML Microservice", "Python 3.11 + Flask 3.0", "Driver risk prediction & telemetry anomaly detection", "Flask-CORS, Rule-Based Scoring Engine, Anomaly Classifier"],
        ["Email Gateway", "Spring Boot Starter Mail", "SMTP email dispatch for critical alerts", "JavaMailSender, MIME multipart, Gmail TLS integration"],
        ["SMS Gateway", "MSG91 REST API", "Instant carrier SMS delivery to drivers and managers", "HTTP REST client, dynamic OTP/alert template binding"],
        ["Build & Package", "Apache Maven 3.9+", "Backend dependency management and packaging", "Maven Compiler Plugin, Spring Boot Maven Plugin"]
    ]
    create_table(doc, tech_headers, tech_data, [Inches(1.5), Inches(1.8), Inches(2.0), Inches(1.7)])

    # =========================================================================
    # SECTION 8: SYSTEM ARCHITECTURE & INFORMATION FLOW
    # =========================================================================
    add_h1(doc, "6. System Architecture & Information Flow")
    add_p(doc, 
        "TrackFleet utilizes a decoupled, multi-tier architectural blueprint designed for horizontal scalability, high reliability, "
        "and clear separation of concerns. The platform combines a Spring Boot monolith for core enterprise operations with an auxiliary "
        "Python Flask microservice for machine learning and behavioral anomaly detection.")

    add_h2(doc, "6.1 Architectural Tier Breakdown")
    add_bullet(doc, "Presentation Tier (Client)", 
        "Built in React 19, running in the user's browser. Communicates strictly via asynchronous JSON REST calls to the Spring Boot API Gateway.")
    add_bullet(doc, "Application Tier (Spring Boot Monolith)", 
        "Handles API request routing, authentication, business rules, spatial computations, report generation via iText, and event dispatching.")
    add_bullet(doc, "Analytics & AI Tier (Python Microservice)", 
        "Lightweight Flask microservice running on port 8000. Provides specialized endpoints for telemetry anomaly detection and predictive scoring.")
    add_bullet(doc, "Data Persistence Tier (PostgreSQL + PostGIS)", 
        "Houses core relational data models, GPS telemetry tables with spatial indexes, and audit logs. Executes high-performance spatial joins.")
    add_bullet(doc, "External Integration Tier", 
        "Integrates with external telecommunications infrastructure: SMTP servers for email and MSG91 SMS gateway for phone notifications.")

    add_h2(doc, "6.2 Real-Time Telemetry Ingestion & Alerting Pipeline")
    add_p(doc, "The end-to-end ingestion lifecycle operates through an 8-stage pipeline:")
    add_bullet(doc, "Stage 1: Packet Acquisition", "GPS tracker or CSV simulator sends JSON telemetry payload (lat, lon, speed, ignition, battery, timestamp).")
    add_bullet(doc, "Stage 2: Schema & Boundary Validation", "Coordinates are verified within valid terrestrial bounds (-90 to +90 lat, -180 to +180 lon), timestamps are normalized to Asia/Kolkata UTC+05:30.")
    add_bullet(doc, "Stage 3: Spatial Indexing & Map Matching", "PostGIS calculates distance from previous packet. Snaps point to nearest OpenStreetMap highway vector.")
    add_bullet(doc, "Stage 4: Trip State Machine & Segmentation", "Trip engine evaluates ignition status. If ignition switches from OFF to ON, a new Trip record is instantiated. If OFF, the active trip is finalized.")
    add_bullet(doc, "Stage 5: Speed Limit Evaluation & Violation Check", "Compares telemetry speed against road speed limit. If speed > limit + 10%, an overspeed anomaly is flagged.")
    add_bullet(doc, "Stage 6: Anomaly Detection", "Evaluates longitudinal acceleration. If acceleration < -3.5 m/s², Harsh Braking Alert is generated; if > +3.0 m/s², Harsh Acceleration Alert is triggered.")
    add_bullet(doc, "Stage 7: Persistence & Stats Accumulation", "Stores clean telemetry into telemetry table. Increments daily driver counters in driver_daily_stats.")
    add_bullet(doc, "Stage 8: Notification Dispatch", "If alert severity is CRITICAL or HIGH, asynchronous workers trigger SMS via MSG91 and Email via SMTP.")

    # =========================================================================
    # SECTION 9: REPOSITORY & PROJECT DIRECTORY STRUCTURE
    # =========================================================================
    add_h1(doc, "7. Repository & Project Directory Structure")
    add_p(doc, "The TrackFleet repository is cleanly organized into modular subdirectories:")

    tree_headers = ["Directory / File Path", "Module / Domain", "Detailed Description & Responsibility"]
    tree_data = [
        ["frontend/", "User Interface", "Complete React 19 + Vite 8 SPA codebase."],
        ["  ├── src/api/", "HTTP Services", "Axios client instances and endpoint functions (vehicles.js, drivers.js, reports.js, penalties.js)."],
        ["  ├── src/components/", "UI Components", "Reusable UI widgets: Sidebar, ChatbotWidget, StatCard, PageHeader."],
        ["  ├── src/pages/", "Application Views", "Full page views: Dashboard, Vehicles, Drivers, Trips, Alerts, Reports, Settings."],
        ["  ├── src/App.jsx", "Root Router", "Defines BrowserRouter routes and layout wrapper."],
        ["  └── package.json", "Node Dependencies", "Defines React, Vite, Lucide, Axios, and Bootstrap dependencies."],
        ["backend-spring/", "Core Backend", "Spring Boot 3.2.5 enterprise application."],
        ["  ├── src/main/java/.../controller/", "REST Controllers", "13 Spring RestControllers exposing CRUD and business operations."],
        ["  ├── src/main/java/.../service/", "Business Services", "13 Service classes handling transactions, safety scoring, reports, and AI chat."],
        ["  ├── src/main/java/.../entity/", "Domain Entities", "14 JPA Entities mapped to PostgreSQL tables via Hibernate."],
        ["  ├── src/main/java/.../repository/", "Data Access", "9 Spring Data JPA repositories with JPQL and native PostGIS spatial queries."],
        ["  ├── src/main/java/.../dto/", "Data Transfer Objects", "Strongly-typed DTOs for request payloads and API responses."],
        ["  ├── src/main/resources/db/migration/", "Flyway SQL", "Versioned database migrations (V1__initial_schema.sql to V7__audit.sql)."],
        ["  └── pom.xml", "Maven Configuration", "Defines Spring Boot starters, PostgreSQL driver, PostGIS Hibernate, and iText 7."],
        ["ai-service/", "Python Microservice", "Flask-based AI microservice for risk prediction and anomaly classification."],
        ["  ├── main.py", "Flask Application", "Exposes /predict, /analyze-driver, and /anomaly-detection endpoints."],
        ["  └── requirements.txt", "Python Packages", "Lists Flask, flask-cors, numpy, and scikit-learn."],
        ["data/", "Sample Datasets", "Synthetic and real-world GPS telemetry CSV files for testing and batch import."],
        ["scripts/", "Automation Tools", "Database seed scripts, environment validation scripts, and documentation generators."],
        ["reports/generated/", "PDF Output Storage", "Local file system directory where generated iText PDF reports are persisted."],
        [".env / .env.example", "Configuration", "Master environment configuration for ports, database credentials, and API keys."]
    ]
    create_table(doc, tree_headers, tree_data, [Inches(2.0), Inches(1.5), Inches(3.5)])

    # =========================================================================
    # SECTION 10: FRONTEND APPLICATION PAGE-BY-PAGE DOCUMENTATION
    # =========================================================================
    add_h1(doc, "8. Frontend Application Page-by-Page Documentation")
    add_p(doc, "The TrackFleet frontend provides a unified, polished interface crafted with clean typography, dynamic status chips, and intuitive controls:")

    add_h2(doc, "8.1 Executive Dashboard (Route: /)")
    add_p(doc, "The operational nerve center of TrackFleet. Designed for instant situational awareness upon login:")
    add_bullet(doc, "Fleet KPI Metric Cards", "Displays Total Active Vehicles, Fleet Odometer, Trips Today, and Open Alerts with percentage changes.")
    add_bullet(doc, "Live Vehicle Status Table", "Shows real-time location, current speed with color-coded speed bars, ignition state, and assigned driver.")
    add_bullet(doc, "Top Safest Drivers Leaderboard", "Ranks top drivers by normalized safety score with visual status chips (Excellent, Average, Needs Improvement).")
    add_bullet(doc, "Recent Alerts Stream", "Displays the most recent 5 critical alerts with immediate resolution buttons.")

    add_h2(doc, "8.2 Fleet Vehicles View (Route: /vehicles)")
    add_bullet(doc, "Interactive Grid & Table View", "Toggle between high-density table and rich visual vehicle cards.")
    add_bullet(doc, "Real-Time Telemetry Bar", "Displays dynamic speed progress bars (Green < 60 km/h, Amber 60-80 km/h, Red > 80 km/h).")
    add_bullet(doc, "Geospatial Quick Links", "Direct clickable link mapping current latitude and longitude to Google Maps and OpenStreetMap.")
    add_bullet(doc, "Fleet Filtering & Search", "Instant client-side search by Vehicle Registration (e.g. MH03AC4582), Code (VH001), Make/Model, or Type.")

    add_h2(doc, "8.3 Drivers Management View (Route: /drivers)")
    add_bullet(doc, "Driver Profiles & Licenses", "Shows Driver Name, Code (e.g. DRV001), Phone, License Number, and Joining Date.")
    add_bullet(doc, "Safety Performance Scoring", "Displays calculated safety score (0-100), total trips, total distance covered, and incident count.")
    add_bullet(doc, "Driver Status Indicator", "Active vs. Inactive state badge. Direct button to generate individual Driver PDF Report.")

    add_h2(doc, "8.4 Trip History & Entry/Exit Log (Route: /trips)")
    add_bullet(doc, "Entry/Exit Event Tracking", "Records precise start timestamp (Ignition ON / Entry) and end timestamp (Ignition OFF / Exit).")
    add_bullet(doc, "Trip Metrics & Duration", "Detailed breakdown of Total Distance (km), Duration (hh:mm:ss), Moving Time, and Idle Time.")
    add_bullet(doc, "Speed & Violation Analysis", "Captures Max Speed (km/h), Average Speed, and number of overspeed/harsh events per trip.")

    add_h2(doc, "8.5 Alerts & Incident Management (Route: /alerts)")
    add_bullet(doc, "Multi-Criteria Filtering", "Filter alerts by Severity (CRITICAL, HIGH, MEDIUM, LOW), Status (OPEN, ACKNOWLEDGED, RESOLVED), and Alert Type.")
    add_bullet(doc, "Interactive Resolution Workflow", "Dispatchers can click 'Acknowledge' to claim an alert or 'Resolve' with notes upon incident clearing.")
    add_bullet(doc, "Notification Delivery Auditing", "Shows real-time status badges for SMS Delivery (MSG91) and Email Delivery (SMTP) for each alert.")

    add_h2(doc, "8.6 Reports & PDF Export Center (Route: /reports)")
    add_bullet(doc, "Report Scope Selector", "Switch effortlessly between 'Single Driver Report' and 'Fleet Overall Report'.")
    add_bullet(doc, "Period Range Options", "Four standard reporting horizons: Monthly (YYYY-MM), Weekly (ISO Year-Wxx), Daily (YYYY-MM-DD), and Custom Date Range.")
    add_bullet(doc, "Interactive Tabbed Preview", "Before downloading, inspect generated data across 5 tabs: Executive Summary, Vehicles Operated, Trip Log, Alert Analysis, and Live PDF Viewer.")
    add_bullet(doc, "Direct PDF Download & Print", "Generates high-resolution iText 7 PDF with one click, available for download or browser printing.")

    add_h2(doc, "8.7 System Settings & Penalty Governance (Route: /settings)")
    add_bullet(doc, "System Health Dashboard", "Real-time heartbeat indicators for Backend API, PostgreSQL Database, PostGIS Extension, Python AI Service, SMTP Mail, and MSG91 SMS.")
    add_bullet(doc, "Alert Penalty Weights Matrix", "Inline editable table allowing managers to modify penalty deduction weights (e.g., change Overspeed penalty from 5.0 to 6.0 points).")
    add_bullet(doc, "Driver Penalty Ledger & Manual Deductions", "Record administrative deductions (e.g. unauthorized route deviation, reckless behavior) with point values and monetary fines.")
    add_bullet(doc, "Revocation & Audit Logging", "Safely revoke erroneous penalties with mandatory reason logging; view immutable audit history.")

    add_h2(doc, "8.8 AI Fleet Assistant Widget (ChatbotWidget)")
    add_bullet(doc, "Always-Accessible Floating Widget", "Collapsible floating widget accessible across all pages with quick-action suggestion pills.")
    add_bullet(doc, "Real-Time PostgreSQL Grounding", "Queries database directly for questions like 'Who is the safest driver?', 'Tell me about VH003', or 'Show overspeed alerts'.")
    add_bullet(doc, "Automated PDF Generation via Chat", "User can prompt 'Generate fleet report for September 2026' to receive an instant direct download link inside the chat.")

    # =========================================================================
    # SECTION 11: BACKEND TECHNICAL ARCHITECTURE & REST API REFERENCE
    # =========================================================================
    add_h1(doc, "9. Backend Technical Architecture & REST API Reference")
    add_p(doc, "The Spring Boot 3.2.5 backend provides a robust suite of 13 REST controllers exposing over 45 endpoints:")

    api_headers = ["Controller", "HTTP Method", "Endpoint URI", "Functionality & Return Type"]
    api_data = [
        ["VehicleController", "GET", "/api/vehicles", "List all vehicles with assigned drivers and latest status"],
        ["VehicleController", "GET", "/api/vehicles/{id}", "Get detailed vehicle record by ID with telemetry summary"],
        ["VehicleController", "POST", "/api/vehicles", "Register new fleet vehicle (IMEI, Reg Number, Model, Type)"],
        ["VehicleController", "PUT", "/api/vehicles/{id}", "Update vehicle specifications, odometer, or driver assignment"],
        ["VehicleController", "GET", "/api/vehicles/{id}/telemetry/latest", "Get the most recent GPS coordinate and speed for vehicle"],
        ["DriverController", "GET", "/api/drivers", "List all registered drivers with active status"],
        ["DriverController", "GET", "/api/drivers/{id}", "Get driver details including safety score and license information"],
        ["DriverController", "GET", "/api/drivers/ranking", "Get driver safety leaderboard ranked by score in date range"],
        ["DriverController", "POST", "/api/drivers", "Create new driver profile with contact and license details"],
        ["TripController", "GET", "/api/trips", "Paginated list of historical trips with start/end entry-exit data"],
        ["TripController", "GET", "/api/trips/{id}", "Get single trip details including trajectory waypoints and alerts"],
        ["TripController", "GET", "/api/trips/vehicle/{id}", "Fetch trips for a specific vehicle with date range filter"],
        ["TelemetryController", "POST", "/api/telemetry", "Ingest real-time GPS telemetry point with map matching"],
        ["TelemetryController", "POST", "/api/telemetry/batch", "Batch ingest historical telemetry records from CSV simulation"],
        ["AlertController", "GET", "/api/alerts", "Retrieve alerts with filtering by severity, status, and alert type"],
        ["AlertController", "PUT", "/api/alerts/{id}/acknowledge", "Mark alert as acknowledged by dispatcher"],
        ["AlertController", "PUT", "/api/alerts/{id}/resolve", "Resolve alert with dispatcher resolution notes"],
        ["ReportController", "POST", "/api/reports/driver", "Generate structured driver report data and trigger PDF generation"],
        ["ReportController", "POST", "/api/reports/fleet/{type}", "Generate comprehensive 4-section overall fleet report"],
        ["ReportController", "GET", "/api/reports/download/{id}", "Stream generated PDF binary file for browser download"],
        ["PenaltyController", "GET", "/api/penalties/configs", "Fetch all active alert penalty weights and standard fines"],
        ["PenaltyController", "PUT", "/api/penalties/configs/{id}", "Update specific alert penalty weight and monetary fine"],
        ["PenaltyController", "GET", "/api/penalties", "Retrieve all driver penalty records with optional driver filter"],
        ["PenaltyController", "POST", "/api/penalties", "Record manual manager penalty deduction against driver"],
        ["PenaltyController", "POST", "/api/penalties/{id}/revoke", "Revoke an active penalty record with audit log tracking"],
        ["ChatController", "POST", "/api/chat", "Process conversational natural language prompt via ChatService"],
        ["HealthController", "GET", "/api/health", "Comprehensive system health check (DB, PostGIS, AI, Mail, SMS)"]
    ]
    create_table(doc, api_headers, api_data, [Inches(1.8), Inches(1.0), Inches(2.2), Inches(2.0)])

    # =========================================================================
    # SECTION 12: DATABASE SCHEMA & POSTGIS SPATIAL MODELING
    # =========================================================================
    add_h1(doc, "10. Database Schema & PostGIS Spatial Modeling")
    add_p(doc, 
        "TrackFleet employs PostgreSQL 17 with the PostGIS 3.4 spatial extension. Relational tables are organized with strict referential "
        "integrity, foreign key constraints, and performance-tuned indexes. Schema evolution is managed deterministically through Flyway migrations.")

    add_h2(doc, "10.1 Database Tables Specification")
    db_headers = ["Table Name", "Primary Key", "Key Columns & Data Types", "Foreign Keys & Relationships"]
    db_data = [
        ["drivers", "id (BIGSERIAL)", "code VARCHAR(50), name VARCHAR(100), phone VARCHAR(20), license_number VARCHAR(50), active BOOLEAN", "1:N with vehicles, trips, driver_penalties"],
        ["vehicles", "id (BIGSERIAL)", "code VARCHAR(50), imei VARCHAR(20), registration_number VARCHAR(50), vehicle_type VARCHAR(50), odometer_km DECIMAL", "FK driver_id -> drivers(id)"],
        ["trips", "id (BIGSERIAL)", "start_time TIMESTAMPTZ, end_time TIMESTAMPTZ, status VARCHAR(20), distance_km DECIMAL, max_speed_kmph DECIMAL", "FK vehicle_id -> vehicles, FK driver_id -> drivers"],
        ["telemetry", "id (BIGSERIAL)", "recorded_at TIMESTAMPTZ, latitude DECIMAL, longitude DECIMAL, speed_kmph DECIMAL, ignition BOOLEAN, road_name VARCHAR", "FK vehicle_id -> vehicles, FK trip_id -> trips"],
        ["alerts", "id (BIGSERIAL)", "alert_type VARCHAR(100), severity VARCHAR(20), status VARCHAR(20), speed_recorded DECIMAL, latitude DECIMAL", "FK vehicle_id -> vehicles, FK driver_id -> drivers"],
        ["driver_daily_stats", "id (BIGSERIAL)", "stat_date DATE, trip_count INT, total_distance_km DECIMAL, overspeed_events INT, safety_score DECIMAL", "FK driver_id -> drivers(id), UNIQUE(driver_id, stat_date)"],
        ["alert_penalty_configs", "id (BIGSERIAL)", "alert_type VARCHAR(100) UNIQUE, weight DECIMAL(5,2), default_fine DECIMAL(10,2), is_active BOOLEAN", "Config table referenced by SafetyScoreService"],
        ["driver_penalties", "id (BIGSERIAL)", "penalty_type VARCHAR(50), points_deducted DECIMAL, fine_amount DECIMAL, is_revoked BOOLEAN", "FK driver_id -> drivers, FK alert_id -> alerts, FK trip_id -> trips"],
        ["penalty_audit_logs", "id (BIGSERIAL)", "action VARCHAR(50), reason TEXT, previous_values JSONB, created_at TIMESTAMPTZ", "FK penalty_id -> driver_penalties(id)"],
        ["cost_records", "id (BIGSERIAL)", "cost_type VARCHAR(50), amount DECIMAL(10,2), cost_date DATE, notes TEXT", "FK vehicle_id -> vehicles(id)"],
        ["maintenance_records", "id (BIGSERIAL)", "maintenance_type VARCHAR(50), cost DECIMAL(10,2), scheduled_date DATE, completed BOOLEAN", "FK vehicle_id -> vehicles(id)"]
    ]
    create_table(doc, db_headers, db_data, [Inches(1.8), Inches(1.3), Inches(2.5), Inches(1.4)])

    add_h2(doc, "10.2 Flyway Database Migrations Evolution")
    add_bullet(doc, "V1__initial_schema.sql", "Creates base tables (drivers, vehicles, trips, telemetry, alerts, rejected_telemetry) and enables PostGIS extension.")
    add_bullet(doc, "V2__add_sensor_tables.sql", "Adds sensor_data table for OBD-II diagnostic trouble codes and secondary sensors.")
    add_bullet(doc, "V3__add_indexes.sql", "Creates B-Tree indexes on recorded_at, vehicle_id, driver_id, and GiST spatial indexes on GPS coordinates.")
    add_bullet(doc, "V4__seed_reference_data.sql", "Seeds initial vehicles (VH001 to VH005), drivers (DRV001 to DRV005), and initial alert configs.")
    add_bullet(doc, "V5__add_created_at_to_driver_daily_stats.sql", "Adds created_at and updated_at audit timestamps to driver daily statistics.")
    add_bullet(doc, "V6__increase_alert_reference_length.sql", "Widens alert reference and message columns to prevent string truncation during complex incidents.")
    add_bullet(doc, "V7__add_penalty_management_and_audit.sql", "Introduces dynamic penalty governance tables: alert_penalty_configs, driver_penalties, and penalty_audit_logs.")

    # =========================================================================
    # SECTION 13: STANDARDIZED SAFETY SCORING & DYNAMIC PENALTY GOVERNANCE
    # =========================================================================
    add_h1(doc, "11. Standardized Safety Scoring & Dynamic Penalty Governance")
    add_p(doc, 
        "A core innovation of TrackFleet is its mathematically rigorous driver safety scoring formula implemented in SafetyScoreService.java. "
        "Unlike crude alert-counting algorithms, TrackFleet normalizes behavioral violations by mileage driven, ensuring statistical equity.")

    add_h2(doc, "11.1 Mathematical Scoring Formulation")
    add_p(doc, "The driver safety score is calculated according to the following deterministic formula:")

    add_callout(doc,
        "STANDARDIZED FORMULA:\n"
        "Safety Score = 100.00 - Σ [ Normalized_Rate(i) × Configured_Weight(i) ] - Manual_Penalty_Deductions\n\n"
        "Where Normalized_Rate is defined as:\n"
        "• Distance-Based Metrics (Overspeed, Harsh Braking, Harsh Accel, GPS Disconnect):\n"
        "    Rate = (Alert_Count / Total_Distance_Km) × 1,000.0 km\n"
        "• Night Driving Exposure:\n"
        "    Rate = (Night_Driving_Hours / Total_Distance_Km) × 1,000.0 km\n"
        "• Driver Fatigue Exposure:\n"
        "    Rate = (Fatigue_Alert_Count / Total_Trips) × 100 trips\n\n"
        "Clamping Boundary: 0.00 ≤ Final Safety Score ≤ 100.00",
        title="MATHEMATICAL SAFETY SCORE ALGORITHM")

    add_h2(doc, "11.2 Configured Metric Weights & Normalization Basis")
    score_headers = ["Metric Name", "System Alert Type", "Default Weight", "Normalization Unit", "Operational Justification"]
    score_data = [
        ["Overspeed", "Overspeed Alert", "5.0", "per 1,000 km", "Prevents high-speed road fatalities; heavily penalized."],
        ["Harsh Braking", "Harsh Braking Alert", "4.0", "per 1,000 km", "Indicates tailgating, late hazard anticipation, or poor defensive driving."],
        ["Harsh Acceleration", "Harsh Acceleration Alert", "4.0", "per 1,000 km", "Increases fuel consumption and mechanical wear on transmission."],
        ["GPS Disconnect", "GPS Disconnect Alert", "6.0", "per 1,000 km", "Flags potential GPS jamming, hardware tampering, or cellular blackouts."],
        ["Night Driving", "Night Driving Alert", "3.0", "hours / 1,000 km", "Evaluates circadian rhythm fatigue risk during 22:00 to 06:00 window."],
        ["Driver Fatigue", "Fatigue Alert", "8.0", "per 100 trips", "Severe safety hazard triggered by >4 hours of continuous driving without rest."]
    ]
    create_table(doc, score_headers, score_data, [Inches(1.5), Inches(1.8), Inches(1.0), Inches(1.2), Inches(2.0)])

    add_h2(doc, "11.3 Performance Categorization")
    add_bullet(doc, "Excellent (Score: 80.00 - 100.00)", "Exemplary defensive driving record. Eligible for driver safety bonuses.")
    add_bullet(doc, "Average (Score: 60.00 - 79.99)", "Acceptable driving behavior with occasional minor infractions. Standard monitoring.")
    add_bullet(doc, "Needs Improvement (Score: 0.00 - 59.99)", "High-risk driver accumulating frequent infractions. Requires mandatory safety training.")

    # =========================================================================
    # SECTION 14: DATA IMPORT, TELEMETRY INGESTION & SPATIAL PREPROCESSING
    # =========================================================================
    add_h1(doc, "12. Data Import, Telemetry Ingestion & Spatial Preprocessing")
    add_p(doc, 
        "TrackFleet supports both real-time IoT GPS telemetry ingestion and batch ingestion of historical CSV log files. "
        "Ingested telemetry undergoes multi-stage sanity verification and spatial enrichment before being accepted into the database.")

    add_bullet(doc, "Geographic Coordinate Bounds Validation", 
        "Coordinates are strictly validated against WGS-84 reference geometry: Latitude must lie in [-90.0, +90.0] and Longitude in [-180.0, +180.0]. Invalid or null coordinates are rejected and logged into rejected_telemetry.")

    add_bullet(doc, "Speed Clamping & GPS Jitter Filtering", 
        "Speed readings exceeding 180 km/h are flagged for anomaly inspection. GPS jitter (telemetry points reporting high displacement within 0 seconds) are filtered out using Haversine temporal distance filtering.")

    add_bullet(doc, "OpenStreetMap Road Snapping & Reverse Geocoding", 
        "Using PostGIS ST_DWithin spatial queries, GPS coordinates are snapped to the nearest road centerlines from OpenStreetMap road networks, retrieving highway type, street name, and official speed limit.")

    add_bullet(doc, "Automatic Trip Segmentation State Machine", 
        "Trips are automatically delineated without driver intervention. When an ignition ON event is detected after a vehicle has been stationary for >5 minutes, a new trip is initiated. When ignition switches OFF, the trip is marked COMPLETED.")

    # =========================================================================
    # SECTION 15: PYTHON AI / ML MICROSERVICE ARCHITECTURE
    # =========================================================================
    add_h1(doc, "13. Python AI / ML Microservice Architecture")
    add_p(doc, 
        "The Python AI microservice (ai-service/main.py) provides machine learning and behavioral anomaly detection capabilities "
        "via a lightweight Flask HTTP microservice running on port 8000.")

    add_h2(doc, "13.1 Microservice Endpoints")
    ai_headers = ["Endpoint", "HTTP Method", "Input Payload", "Response Payload & Logic"]
    ai_data = [
        ["/health", "GET", "None", "{'status': 'ok', 'service': 'GPS Fleet AI/ML Service', 'version': '1.0.0'}"],
        ["/predict", "POST", "{'overspeed_events': 3, 'harsh_braking_events': 2, ...}", "Predicts safety score and itemized deduction breakdown."],
        ["/analyze-driver", "POST", "{'driver_id': 1, 'overspeed_events': 4, ...}", "Full behavioral diagnostic: Risk Level (LOW, MED, HIGH), recommendations list."],
        ["/anomaly-detection", "POST", "{'telemetry_points': [{'speed': 95, 'speed_limit': 80}, ...]}", "Scans trajectory points for sudden deceleration (< -3.5 m/s²) and severe overspeeding."]
    ]
    create_table(doc, ai_headers, ai_data, [Inches(1.5), Inches(1.0), Inches(2.2), Inches(2.3)])

    add_h2(doc, "13.2 Automated Driver Recommendations Engine")
    add_p(doc, 
        "Integrated within Spring Boot's ChatService, TrackFleet employs a grounding algorithm that queries actual PostgreSQL trip and alert history "
        "to recommend the optimal driver for specific operational assignments: Long-Distance Trips, High-Value Cargo Transport, or Minimum Alert Frequency.")

    # =========================================================================
    # SECTION 16: STEP-BY-STEP INSTALLATION & DEPLOYMENT MANUAL
    # =========================================================================
    add_h1(doc, "14. Step-by-Step Installation & Deployment Manual")
    add_p(doc, "This section outlines the exact terminal commands required to configure and run TrackFleet on a macOS development environment:")

    add_h2(doc, "14.1 System Prerequisites")
    add_bullet(doc, "Java Development Kit", "OpenJDK 17 or Oracle JDK 17 (verified via `java -version`)")
    add_bullet(doc, "Node.js & npm", "Node.js 18+ and npm 9+ (verified via `node -v` and `npm -v`)")
    add_bullet(doc, "Python Runtime", "Python 3.10+ with pip3 (verified via `python3 --version`)")
    add_bullet(doc, "Database", "PostgreSQL 17 with PostGIS extension installed (verified via `psql -U postgres`)")

    add_h2(doc, "14.2 Database Initialization")
    add_p(doc, "Open Terminal and execute the following SQL commands to prepare the PostgreSQL database:")
    add_callout(doc, 
        "CREATE DATABASE gps_tracking;\n"
        "\\c gps_tracking;\n"
        "CREATE EXTENSION IF NOT EXISTS postgis;\n"
        "CREATE EXTENSION IF NOT EXISTS postgis_topology;", 
        title="POSTGRESQL INITIALIZATION SCRIPT")

    add_h2(doc, "14.3 Launching All Three Subsystems")
    add_p(doc, "Run each service in a separate terminal tab:")
    add_bullet(doc, "Tab 1: Python AI Service", "cd ai-service && pip3 install -r requirements.txt && python3 main.py")
    add_bullet(doc, "Tab 2: Spring Boot Backend", "cd backend-spring && ./mvnw spring-boot:run")
    add_bullet(doc, "Tab 3: React Frontend SPA", "cd frontend && npm install && npm run dev")
    add_bullet(doc, "Browser Access", "Navigate to http://localhost:5173 to access the live TrackFleet web application.")

    # =========================================================================
    # SECTION 17: COMPLETE ENVIRONMENT VARIABLES REFERENCE
    # =========================================================================
    add_h1(doc, "15. Complete Environment Variables Reference")
    add_p(doc, "TrackFleet is configured via a centralized `.env` file at the project root:")

    env_headers = ["Variable Name", "Default Value", "Description & Usage"]
    env_data = [
        ["DB_HOST", "localhost", "Hostname or IP address of the PostgreSQL server"],
        ["DB_PORT", "5432", "Port number of the PostgreSQL server"],
        ["DB_NAME", "gps_tracking", "Target PostgreSQL database name"],
        ["DB_USERNAME", "postgres", "Database superuser or application username"],
        ["DB_PASSWORD", "postgres", "Database password"],
        ["SERVER_PORT", "8080", "Port on which the Spring Boot REST API listens"],
        ["EMAIL_HOST", "smtp.gmail.com", "SMTP mail server hostname for alert emails"],
        ["EMAIL_PORT", "587", "SMTP port (587 for TLS, 465 for SSL)"],
        ["EMAIL_USER", "your_gmail@gmail.com", "Sender Gmail address"],
        ["EMAIL_PASSWORD", "app_specific_password", "Gmail 16-character App Password (not account password)"],
        ["FLEET_EMAIL", "fleet@trackfleet.com", "Recipient email address for fleet management summaries"],
        ["MANAGER_EMAIL", "manager@trackfleet.com", "Recipient email for critical safety alert notifications"],
        ["MSG91_AUTH_KEY", "your_msg91_auth_key", "MSG91 REST API authentication key for carrier SMS"],
        ["MSG91_SMS_TEMPLATE_ID", "your_template_id", "Approved DLT SMS template ID registered in MSG91"],
        ["MSG91_SENDER_ID", "TRCKFL", "Approved 6-character alphabetic sender header"],
        ["FLEET_PHONE", "919876543200", "Phone number for operational fleet dispatch alerts"],
        ["MANAGER_PHONE", "919876543201", "Phone number for safety manager emergency alerts"],
        ["PYTHON_AI_SERVICE_URL", "http://localhost:8000", "Base URL of the Python Flask AI microservice"],
        ["VITE_API_URL", "http://localhost:8080", "Backend API URL used by the React Axios client"],
        ["REPORTS_DIR", "./reports/generated", "Local server filesystem folder where PDF reports are saved"]
    ]
    create_table(doc, env_headers, env_data, [Inches(2.0), Inches(1.8), Inches(3.2)])

    # =========================================================================
    # SECTION 18: SERVER-SIDE ITEXT 7 CORPORATE PDF REPORTING ENGINE
    # =========================================================================
    add_h1(doc, "16. Server-Side iText 7 Corporate PDF Reporting Engine")
    add_p(doc, 
        "TrackFleet features a server-side PDF generation engine built using iText 7 (com.gps.tracking.service.ReportService). "
        "The engine programmatically assembles multi-page documents styled with corporate design principles:")

    add_bullet(doc, "Document Layout & Geometry", 
        "Uses standard ISO A4 dimensions (595 × 842 points) with 36-point margins. All font sizes, leading, and cell paddings are calibrated for maximum readability.")

    add_bullet(doc, "Executive Color Palette", 
        "Strictly adheres to an executive color palette: Primary Royal Blue (#1E40AF), Slate Header (#0F172A), Table Navy (#1E3A8A), Muted Slate (#64748B), Success Green (#16A34A), Danger Red (#DC2626), and Alternating Zebra (#F8FAFC).")

    add_bullet(doc, "Dynamic Canvas Header & Footer Event Handler", 
        "Implements iText's `IEventHandler` listening to `PdfDocumentEvent.END_PAGE`. Programmatically draws a running header with report metadata and a dynamic footer with 'Page X of Y' pagination.")

    add_h2(doc, "16.1 The Four Canonical Report Sections")
    add_bullet(doc, "Section A: Executive KPI Summary", 
        "High-density metric summary table: Total Trips, Distance Covered, Vehicles Entered/Exited, Overall Safety Score, and Alert Count breakdown.")

    add_bullet(doc, "Section B: Vehicles Operated by Driver", 
        "Historical correlation table showing every vehicle operated by the driver during the reporting period, registration number, trip count, distance, and alerts incurred.")

    add_bullet(doc, "Section C: Comprehensive Trip Log", 
        "Chronological table of completed trips: Start Time, End Time, Vehicle Reg, Distance (km), Duration, Max Speed, and Alert Count.")

    add_bullet(doc, "Section D: Alert & Violation Breakdown", 
        "Categorical analysis of behavioral alerts: Overspeed, Harsh Braking, Harsh Acceleration, GPS Disconnect, Night Driving, and Driver Fatigue.")

    # =========================================================================
    # SECTION 19: TESTING, VERIFICATION & QUALITY ASSURANCE
    # =========================================================================
    add_h1(doc, "17. Testing, Verification & Quality Assurance")
    add_p(doc, "TrackFleet is verified through unit, integration, and end-to-end automated tests:")

    add_bullet(doc, "SafetyScoreServiceTest (JUnit 5)", 
        "Validates mathematical scoring behavior: verifies that a driver with 0 alerts receives a perfect 100.00 score, checks that score never drops below 0.00 even with extreme alerts, and confirms distance normalization equity.")

    add_bullet(doc, "ReportServiceTest (Integration)", 
        "Verifies date resolution across all 4 report periods (Monthly, Weekly, Daily, Custom) and confirms that generated PDF files exist on disk with valid byte sizes.")

    add_bullet(doc, "Health Check Endpoint Verification", 
        "Automated polling of `/api/health` asserts that PostgreSQL, PostGIS, Python AI Service, and Spring Boot are reporting UP status.")

    # =========================================================================
    # SECTION 20: SYSTEM LIMITATIONS, FUTURE ROADMAP & TECHNICAL GLOSSARY
    # =========================================================================
    add_h1(doc, "18. System Limitations, Future Roadmap & Technical Glossary")
    add_p(doc, "While TrackFleet represents an advanced production-ready platform, ongoing evolution is planned:")

    add_h2(doc, "18.1 Current Technical Limitations")
    add_bullet(doc, "Cellular Blind Spots", "In remote highway segments lacking cellular connectivity, GPS devices store telemetry locally and upload in bursts upon reconnection.")
    add_bullet(doc, "Sensor Fusion Constraints", "Current anomaly detection relies on GPS-derived acceleration; integration of raw 3-axis accelerometer hardware will improve harsh braking accuracy.")

    add_h2(doc, "18.2 Future Roadmap Enhancements")
    add_bullet(doc, "Deep Learning Predictive Maintenance", "Train LSTM neural networks on CAN-bus sensor data to predict battery failure and brake pad wear before breakdown.")
    add_bullet(doc, "Electronic Logging Device (ELD) Compliance", "Automate driver hours-of-service compliance reporting in accordance with transport regulatory standards.")
    add_bullet(doc, "Native Mobile Driver Companion App", "Flutter-based cross-platform mobile application allowing drivers to view their safety score and coaching tips in real-time.")

    add_h2(doc, "18.3 Technical Telematics Glossary")
    gloss_headers = ["Telematics Term", "Definition & Operational Context in TrackFleet"]
    gloss_data = [
        ["Telematics", "The convergence of telecommunications and informatics to transmit vehicle tracking and diagnostic data over wireless networks."],
        ["PostGIS", "Spatial database extender for PostgreSQL adding support for geographic objects and high-speed spatial querying."],
        ["Geofencing", "Virtual geographic boundary defined by GPS coordinates that triggers automated alerts when vehicles enter or exit."],
        ["CAN-bus", "Controller Area Network; standard vehicle bus allowing microcontrollers to communicate without a host computer."],
        ["OBD-II", "On-Board Diagnostics standard port providing real-time engine metrics, diagnostic trouble codes, and fuel levels."],
        ["Map Matching", "Algorithmic process of aligning raw GPS trajectory points to real OpenStreetMap road networks."],
        ["Haversine Formula", "Mathematical equation determining the great-circle distance between two points on a sphere given their lat/lon."],
        ["Distance Normalization", "Statistical methodology of dividing incident counts by total kilometers driven to enable fair driver safety comparison."],
        ["GiST Index", "Generalized Search Tree; PostgreSQL index structure enabling sub-millisecond bounding-box spatial search."]
    ]
    create_table(doc, gloss_headers, gloss_data, [Inches(2.2), Inches(4.8)])

    # Save document
    os.makedirs(os.path.dirname(OUTPUT_PATH), exist_ok=True)
    doc.save(OUTPUT_PATH)
    print(f"✅ Successfully created document at: {OUTPUT_PATH}")
    print(f"File size: {os.path.getsize(OUTPUT_PATH):,} bytes")

if __name__ == "__main__":
    build_documentation()
