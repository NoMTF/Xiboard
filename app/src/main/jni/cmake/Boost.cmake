# SPDX-FileCopyrightText: 2015 - 2024 Rime community
#
# SPDX-License-Identifier: GPL-3.0-or-later

set(BOOST_VERSION 1.89.0)
set(BOOST_ARCHIVE "${CMAKE_SOURCE_DIR}/boost-${BOOST_VERSION}.tar.xz")
set(BOOST_SOURCE_DIR "${CMAKE_SOURCE_DIR}/boost")
set(BOOST_EXTRACTED_DIR "${CMAKE_SOURCE_DIR}/boost-${BOOST_VERSION}")

if(NOT EXISTS "${BOOST_ARCHIVE}")
  message(STATUS "Downloading Boost ${BOOST_VERSION} ......")
  file(
    DOWNLOAD
    "https://github.com/boostorg/boost/releases/download/boost-${BOOST_VERSION}/boost-${BOOST_VERSION}-cmake.tar.xz"
    "${BOOST_ARCHIVE}"
    EXPECTED_HASH
      SHA256=67acec02d0d118b5de9eb441f5fb707b3a1cdd884be00ca24b9a73c995511f74
    SHOW_PROGRESS)

  message(STATUS "Remove older version Boost")
  file(REMOVE_RECURSE "${BOOST_SOURCE_DIR}" "${BOOST_EXTRACTED_DIR}")
endif()

if(NOT EXISTS "${BOOST_SOURCE_DIR}")
  message(STATUS "Extracting Boost ${BOOST_VERSION} ......")
  file(ARCHIVE_EXTRACT INPUT "${BOOST_ARCHIVE}" DESTINATION
       "${CMAKE_SOURCE_DIR}")
  file(RENAME "${BOOST_EXTRACTED_DIR}" "${BOOST_SOURCE_DIR}")
endif()

set(BOOST_INCLUDE_LIBRARIES
    algorithm
    crc
    dll
    interprocess
    range
    regex
    scope_exit
    signals2
    utility
    uuid)

add_subdirectory(boost EXCLUDE_FROM_ALL)
