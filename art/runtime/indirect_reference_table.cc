/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "indirect_reference_table-inl.h"

#include "jni_internal.h"
#include "nth_caller_visitor.h"
#include "reference_table.h"
#include "runtime.h"
#include "scoped_thread_state_change.h"
#include "thread.h"
#include "utils.h"
#include "verify_object-inl.h"

#include "base/mutex.h"
#include "base/mutex-inl.h"
#include "base/time_utils.h"
#include "base/timing_logger.h"

#include <cstdlib>
#include <vector>
#include <assert.h>
#include <openssl/md5.h>
#include <strings.h>
#include <cutils/hashmap.h>
#include <utils/CallStack.h>

namespace art {
static constexpr bool kDumpStackOnNonLocalReference = false;

template<typename T>
class MutatorLockedDumpable {
 public:
  explicit MutatorLockedDumpable(T& value)
      SHARED_LOCKS_REQUIRED(Locks::mutator_lock_) : value_(value) {
  }

  void Dump(std::ostream& os) const SHARED_LOCKS_REQUIRED(Locks::mutator_lock_) {
    value_.Dump(os);
  }

 private:
  T& value_;

  DISALLOW_COPY_AND_ASSIGN(MutatorLockedDumpable);
};

template<typename T>
std::ostream& operator<<(std::ostream& os, const MutatorLockedDumpable<T>& rhs)
// TODO: should be SHARED_LOCKS_REQUIRED(Locks::mutator_lock_) however annotalysis
//       currently fails for this.
    NO_THREAD_SAFETY_ANALYSIS {
  rhs.Dump(os);
  return os;
}

void IndirectReferenceTable::AbortIfNoCheckJNI() {
  // If -Xcheck:jni is on, it'll give a more detailed error before aborting.
  JavaVMExt* vm = Runtime::Current()->GetJavaVM();
  if (!vm->IsCheckJniEnabled()) {
    // Otherwise, we want to abort rather than hand back a bad reference.
    LOG(FATAL) << "JNI ERROR (app bug): see above.";
  }
}

IndirectReferenceTable::IndirectReferenceTable(size_t initialCount,
                                               size_t maxCount, IndirectRefKind desiredKind,
                                               bool abort_on_error)
    : kind_(desiredKind),
      max_entries_(maxCount) {
  CHECK_GT(initialCount, 0U);
  CHECK_LE(initialCount, maxCount);
  CHECK_NE(desiredKind, kHandleScopeOrInvalid);

  std::string error_str;
  const size_t table_bytes = maxCount * sizeof(IrtEntry);
  table_mem_map_.reset(MemMap::MapAnonymous("indirect ref table", nullptr, table_bytes,
                                            PROT_READ | PROT_WRITE, false, false, &error_str));
  if (abort_on_error) {
    CHECK(table_mem_map_.get() != nullptr) << error_str;
    CHECK_EQ(table_mem_map_->Size(), table_bytes);
    CHECK(table_mem_map_->Begin() != nullptr);
  } else if (table_mem_map_.get() == nullptr ||
             table_mem_map_->Size() != table_bytes ||
             table_mem_map_->Begin() == nullptr) {
    table_mem_map_.reset();
    LOG(ERROR) << error_str;
    return;
  }
  table_ = reinterpret_cast<IrtEntry*>(table_mem_map_->Begin());
  segment_state_.all = IRT_FIRST_SEGMENT;
/* SPRD: modify 20150915 Spreadtrum of 474961 Add log for global reference table overflow @{ */
  if (max_entries_ == max_available_count) {
      g_hash_map = hashmapCreate(max_available_count - debug_count, PredStrHash, PredStrEquals);
      isEnableDebugTrce = true;
  }
/* 474961 @} */
}
IndirectReferenceTable::~IndirectReferenceTable() {
}

bool IndirectReferenceTable::IsValid() const {
  return table_mem_map_.get() != nullptr;
}

IndirectRef IndirectReferenceTable::Add(uint32_t cookie, mirror::Object* obj) {
  IRTSegmentState prevState;
  prevState.all = cookie;
  size_t topIndex = segment_state_.parts.topIndex;

  CHECK(obj != nullptr);
  VerifyObject(obj);
  DCHECK(table_ != nullptr);
  DCHECK_GE(segment_state_.parts.numHoles, prevState.parts.numHoles);

  if (topIndex == max_entries_) {
/* SPRD: modify 20150915 Spreadtrum of 474961 Add log for global reference table overflow @{ */
      DumpDebugTrace();
/* 474961 @} */
    LOG(FATAL) << "JNI ERROR (app bug): " << kind_ << " table overflow "
               << "(max=" << max_entries_ << ")\n"
               << MutatorLockedDumpable<IndirectReferenceTable>(*this);
  }

  // We know there's enough room in the table.  Now we just need to find
  // the right spot.  If there's a hole, find it and fill it; otherwise,
  // add to the end of the list.
  IndirectRef result;
  int numHoles = segment_state_.parts.numHoles - prevState.parts.numHoles;
  size_t index;
  if (numHoles > 0) {
    DCHECK_GT(topIndex, 1U);
    // Find the first hole; likely to be near the end of the list.
    IrtEntry* pScan = &table_[topIndex - 1];
    DCHECK(!pScan->GetReference()->IsNull());
    --pScan;
    while (!pScan->GetReference()->IsNull()) {
      DCHECK_GE(pScan, table_ + prevState.parts.topIndex);
      --pScan;
    }
    index = pScan - table_;
    segment_state_.parts.numHoles--;
  } else {
    // Add to the end.
    index = topIndex++;
    segment_state_.parts.topIndex = topIndex;
  }
  table_[index].Add(obj);
  result = ToIndirectRef(index);
/* SPRD: modify 20150915 Spreadtrum of 474961 Add log for global reference table overflow @{ */
  if (isEnableDebugTrce && topIndex >= debug_count) {
    AddDebugTrace(result);
  }
/* 474961 @} */
  if ((false)) {
    LOG(INFO) << "+++ added at " << ExtractIndex(result) << " top=" << segment_state_.parts.topIndex
              << " holes=" << segment_state_.parts.numHoles;
  }

  DCHECK(result != nullptr);
  return result;
}

void IndirectReferenceTable::AssertEmpty() {
  for (size_t i = 0; i < Capacity(); ++i) {
    if (!table_[i].GetReference()->IsNull()) {
      ScopedObjectAccess soa(Thread::Current());
      LOG(FATAL) << "Internal Error: non-empty local reference table\n"
                 << MutatorLockedDumpable<IndirectReferenceTable>(*this);
    }
  }
}

// Removes an object. We extract the table offset bits from "iref"
// and zap the corresponding entry, leaving a hole if it's not at the top.
// If the entry is not between the current top index and the bottom index
// specified by the cookie, we don't remove anything. This is the behavior
// required by JNI's DeleteLocalRef function.
// This method is not called when a local frame is popped; this is only used
// for explicit single removals.
// Returns "false" if nothing was removed.
bool IndirectReferenceTable::Remove(uint32_t cookie, IndirectRef iref) {
  IRTSegmentState prevState;
  prevState.all = cookie;
  int topIndex = segment_state_.parts.topIndex;
  int bottomIndex = prevState.parts.topIndex;

  DCHECK(table_ != nullptr);
  DCHECK_GE(segment_state_.parts.numHoles, prevState.parts.numHoles);

  if (GetIndirectRefKind(iref) == kHandleScopeOrInvalid) {
    auto* self = Thread::Current();
    if (self->HandleScopeContains(reinterpret_cast<jobject>(iref))) {
      auto* env = self->GetJniEnv();
      DCHECK(env != nullptr);
      if (env->check_jni) {
        ScopedObjectAccess soa(self);
        LOG(WARNING) << "Attempt to remove non-JNI local reference, dumping thread";
        if (kDumpStackOnNonLocalReference) {
          self->Dump(LOG(WARNING));
        }
      }
      return true;
    }
  }
  const int idx = ExtractIndex(iref);
  if (idx < bottomIndex) {
    // Wrong segment.
    LOG(WARNING) << "Attempt to remove index outside index area (" << idx
                 << " vs " << bottomIndex << "-" << topIndex << ")";
    return false;
  }
  if (idx >= topIndex) {
    // Bad --- stale reference?
    LOG(WARNING) << "Attempt to remove invalid index " << idx
                 << " (bottom=" << bottomIndex << " top=" << topIndex << ")";
    return false;
  }

  if (idx == topIndex - 1) {
    // Top-most entry.  Scan up and consume holes.

    if (!CheckEntry("remove", iref, idx)) {
      return false;
    }
    *table_[idx].GetReference() = GcRoot<mirror::Object>(nullptr);
    int numHoles = segment_state_.parts.numHoles - prevState.parts.numHoles;
    if (numHoles != 0) {
      while (--topIndex > bottomIndex && numHoles != 0) {
        if ((false)) {
          LOG(INFO) << "+++ checking for hole at " << topIndex - 1
                    << " (cookie=" << cookie << ") val="
                    << table_[topIndex - 1].GetReference()->Read<kWithoutReadBarrier>();
        }
        if (!table_[topIndex - 1].GetReference()->IsNull()) {
          break;
        }
        if ((false)) {
          LOG(INFO) << "+++ ate hole at " << (topIndex - 1);
        }
        numHoles--;
      }
      segment_state_.parts.numHoles = numHoles + prevState.parts.numHoles;
      segment_state_.parts.topIndex = topIndex;
    } else {
      segment_state_.parts.topIndex = topIndex-1;
      if ((false)) {
        LOG(INFO) << "+++ ate last entry " << topIndex - 1;
      }
    }
  } else {
    // Not the top-most entry.  This creates a hole.  We null out the entry to prevent somebody
    // from deleting it twice and screwing up the hole count.
    if (table_[idx].GetReference()->IsNull()) {
      LOG(INFO) << "--- WEIRD: removing null entry " << idx;
      return false;
    }
    if (!CheckEntry("remove", iref, idx)) {
      return false;
    }
    *table_[idx].GetReference() = GcRoot<mirror::Object>(nullptr);
    segment_state_.parts.numHoles++;
    if ((false)) {
      LOG(INFO) << "+++ left hole at " << idx << ", holes=" << segment_state_.parts.numHoles;
    }
  }
/* SPRD: modify 20150915 Spreadtrum of 474961 Add log for global reference table overflow @{ */
  if (isEnableDebugTrce && topIndex >= static_cast<int>(debug_count)) {
    Thread* self = Thread::Current();
    ReaderMutexLock mu(self, *Locks::mutator_lock_);
    RemoveDebugTrace(iref);
  }
/* 474961 @} */
  return true;
}

void IndirectReferenceTable::Trim() {
  const size_t top_index = Capacity();
  auto* release_start = AlignUp(reinterpret_cast<uint8_t*>(&table_[top_index]), kPageSize);
  uint8_t* release_end = table_mem_map_->End();
  madvise(release_start, release_end - release_start, MADV_DONTNEED);
}

void IndirectReferenceTable::VisitRoots(RootVisitor* visitor, const RootInfo& root_info) {
  BufferedRootVisitor<kDefaultBufferedRootCount> root_visitor(visitor, root_info);
  for (auto ref : *this) {
    if (!ref->IsNull()) {
      root_visitor.VisitRoot(*ref);
      DCHECK(!ref->IsNull());
    }
  }
}

void IndirectReferenceTable::Dump(std::ostream& os) const {
  os << kind_ << " table dump:\n";
  ReferenceTable::Table entries;
  for (size_t i = 0; i < Capacity(); ++i) {
    mirror::Object* obj = table_[i].GetReference()->Read<kWithoutReadBarrier>();
    if (obj != nullptr) {
      obj = table_[i].GetReference()->Read();
      entries.push_back(GcRoot<mirror::Object>(obj));
    }
  }
  ReferenceTable::Dump(os, entries);
}
/* SPRD: modify 20150915 Spreadtrum of 474961 Add log for global reference table overflow @{ */
int IndirectReferenceTable::PredStrHash(void *key) {
    return hashmapHash(key, strlen(reinterpret_cast<char*>(key)));
}
bool IndirectReferenceTable::PredStrEquals(void *key_a, void *key_b) {
    return strcmp(reinterpret_cast<char*>(key_a), reinterpret_cast<char*>(key_b)) == 0;
}

int IndirectReferenceTable::PredSortTrace(const void * t1, const void * t2) {
    return (*(reinterpret_cast<Debug_info **>(const_cast<void*>(t2))))->count - (*(reinterpret_cast<Debug_info **>(const_cast<void*>(t1))))->count;
}
bool IndirectReferenceTable::PredClollectMapValue(void * key, void * value, void * context) {
    if (false) {
      LOG(INFO) << "pred_collect_map_value --key = " << key;
    }
    Debug_info ** traces = reinterpret_cast<Debug_info **>(*(reinterpret_cast<size_t *>(context)));
    int * offset = reinterpret_cast<int *>((reinterpret_cast<int *>(context) + 1));
    traces[*offset] = reinterpret_cast<Debug_info *>(value);
    *offset = *offset + 1;
    return true;
}

void IndirectReferenceTable::DumpDebugTrace() {
    ALOGE("IndirectReference_Trace: ****** dump begin ******");
    int hash_size = hashmapSize(g_hash_map);
    Debug_info * traces[hash_size];
    bzero(traces, sizeof(Debug_info *) * hash_size);
    size_t context[2] = {reinterpret_cast<size_t>(traces), 0};
    hashmapForEach(g_hash_map, PredClollectMapValue, reinterpret_cast<void *>(context));
    qsort(traces, hash_size, sizeof(Debug_info *), PredSortTrace);
      for (int i = 0; i < hash_size; i++) {
          Debug_info * _debug_info = traces[i];
          ALOGE("- Trace %d Begin: ------ dump trace ------", i);
          ALOGE("- Trace count  : %d", _debug_info->count);
          ALOGE("- java trace  :\n%s", _debug_info->java_stack_trace);
          ALOGE("- native trace  :\n%s", _debug_info->native_stack_trace);
          if (i == 20) break;
        }
      ALOGE("IndirectReference_Trace: ****** dump end ******");
}

void IndirectReferenceTable::AddDebugTrace(IndirectRef result) {
    android::CallStack stack;
    stack.update(4);
    std::ostringstream java_stack;
    art::Runtime::DumpJavaStack(java_stack);
    char* md5_sum = LocalMd5(reinterpret_cast<const char*>(stack.toString("").string()), reinterpret_cast<const char*>(java_stack.str().c_str()));
    Debug_info* debug_info = reinterpret_cast<Debug_info *>(hashmapGet(g_hash_map , md5_sum));
    if (debug_info == NULL) {
        debug_info = reinterpret_cast<Debug_info *>(malloc(sizeof(Debug_info)));
        debug_info->count = 1;
        debug_info->native_stack_trace = strdup(stack.toString("").string());
        debug_info->java_stack_trace = strdup(java_stack.str().c_str());
        hashmapPut(g_hash_map, md5_sum, debug_info);
        debug_data.sum_md5 = md5_sum;
        debug_data.dRference = result;
        debug_data_list.push_back(debug_data);
    } else {
        debug_info->count++;
        debug_data.sum_md5 = md5_sum;
        debug_data.dRference = result;
        debug_data_list.push_back(debug_data);
    }
}
void IndirectReferenceTable::RemoveDebugTrace(IndirectRef result) {
    int index =-1;
    char * md5_sum = nullptr;
    for (Debug_data& dData : debug_data_list) {
      index++;
      if (dData.dRference == result) {
        md5_sum = dData.sum_md5;
        break;
      }
    }
    if (md5_sum == nullptr) {
      return;
    }
    Debug_info* debug_info = reinterpret_cast<Debug_info *>(hashmapGet(g_hash_map, md5_sum));
    if (debug_info == NULL) {  // assert or do nothing??? there are also some nullpointers
      LOG(INFO) << "ReferenceTable::RemoveDebugTrace --debug_info == NULL -- return ! no crash";
      return;
    }
    debug_info->count--;
    debug_data_list.erase(debug_data_list.begin() + index);
    if (debug_info->count == 0) {
        free(debug_info->native_stack_trace);
        free(debug_info->java_stack_trace);
        free(debug_info);
        hashmapRemove(g_hash_map, md5_sum);
        free(md5_sum);
    }
}
#define MD_SIZE 16
char* IndirectReferenceTable::LocalMd5(const char * data , const char * data2) {
    MD5_CTX ctx;
    unsigned char md[MD_SIZE] = {0};
    MD5_Init(&ctx);
    MD5_Update(&ctx , data , strlen(data));
    MD5_Update(&ctx , data2 , strlen(data2));
    MD5_Final(md , &ctx);
    char* ret = reinterpret_cast<char*>(malloc(MD_SIZE*2+1));
    bzero(ret , MD_SIZE*2+1);
    char tmp[3]= {0};
    for ( int i = 0 ; i < MD_SIZE ; i++ ) {
      sprintf(tmp , "%02X" , md[i]);
      strcat(ret , tmp);
    }
    return ret;
}
/* 474961 @} */
}  // namespace art
