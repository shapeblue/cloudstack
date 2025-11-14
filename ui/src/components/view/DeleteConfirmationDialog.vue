// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

<template>
    <a-modal
    :visible="visible"
    :title="title"
    :closable="!loading"
    :maskClosable="!loading"
    :width="500"
    @cancel="handleCancel"
    wrapClassName="delete-confirmation-modal"
  >
    <div class="confirmation-content">
      <!-- Existing Message (from API or predefined) -->
      <a-alert
        v-if="message"
        type="error"
        :message="message"
        show-icon
        class="message-alert"
      />

      <!-- Warning Section -->
      <div class="warning-section">
        <p class="warning-text">
          <exclamation-circle-outlined class="warning-icon" />
          This action <strong class="danger-text">CANNOT</strong> be undone.
        </p>
      </div>

      <!-- Type to Confirm Input -->
      <div class="confirmation-input-section">
        <label class="input-label">
          Please type <code class="confirm-code">{{ resourceName }}</code> to confirm deletion:
        </label>
        <a-input
          v-model:value="confirmationText"
          :placeholder="`Type ${resourceName} here`"
          :disabled="loading"
          @pressEnter="handleConfirm"
          ref="confirmInput"
          class="confirmation-input"
        />
        <div v-if="showValidationError" class="error-message">
          <close-circle-outlined />
          <span>Name doesn't match. Please type exactly: <strong>{{ resourceName }}</strong></span>
        </div>
      </div>
    </div>

    <template #footer>
      <a-button
        key="cancel"
        @click="handleCancel"
        :disabled="loading"
      >
        {{ $t('label.cancel') }}
      </a-button>
      <a-button
        key="submit"
        type="primary"
        danger
        :disabled="!isConfirmationValid"
        :loading="loading"
        @click="handleConfirm"
      >
        {{ confirmButtonText || $t('label.ok') }}
      </a-button>
    </template>
  </a-modal>
</template>

<script>
import { ExclamationCircleOutlined, CloseCircleOutlined } from '@ant-design/icons-vue'

export default {
  name: 'DeleteConfirmationDialog',
  components: {
    ExclamationCircleOutlined,
    CloseCircleOutlined
  },
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    title: {
      type: String,
      required: true
    },
    resourceName: {
      type: String,
      required: true
    },
    message: {
      type: String,
      default: ''
    },
    loading: {
      type: Boolean,
      default: false
    },
    confirmButtonText: {
      type: String,
      default: null
    },
    // New prop: require typing to confirm (false for backward compatibility)
    requiresTyping: {
      type: Boolean,
      default: true
    }
  },
  data () {
    return {
      confirmationText: ''
    }
  },
  computed: {
    isConfirmationValid () {
      // If typing is not required, always valid (backward compatible)
      if (!this.requiresTyping) {
        return true
      }
      // Otherwise, must match exactly
      return this.confirmationText === this.resourceName
    },
    showValidationError () {
      return this.requiresTyping &&
             this.confirmationText.length > 0 &&
             !this.isConfirmationValid
    }
  },
  watch: {
    visible (newVal) {
      if (newVal) {
        this.confirmationText = ''
        this.$nextTick(() => {
          if (this.$refs.confirmInput && this.requiresTyping) {
            this.$refs.confirmInput.focus()
          }
        })
      }
    }
  },
  methods: {
    handleConfirm () {
      if (this.isConfirmationValid && !this.loading) {
        this.$emit('ok')
      }
    },
    handleCancel () {
      if (!this.loading) {
        this.confirmationText = ''
        this.$emit('cancel')
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.confirmation-content {
  .message-alert {
    margin-bottom: 16px;
  }

  .warning-section {
    margin-bottom: 20px;
    padding: 12px;
    background-color: #fff7e6;
    border: 1px solid #ffd591;
    border-radius: 4px;

    .warning-text {
      margin: 0;
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      color: rgba(0, 0, 0, 0.85);

      .warning-icon {
        color: #faad14;
        font-size: 16px;
      }

      .danger-text {
        color: #ff4d4f;
        font-weight: 600;
      }
    }
  }

  .confirmation-input-section {
    .input-label {
      display: block;
      margin-bottom: 8px;
      font-size: 14px;
      font-weight: 500;
      color: rgba(0, 0, 0, 0.85);

      .confirm-code {
        padding: 2px 6px;
        background-color: #f5f5f5;
        border: 1px solid #d9d9d9;
        border-radius: 2px;
        font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
        font-size: 13px;
        color: #ff4d4f;
        font-weight: 600;
      }
    }

    .confirmation-input {
      width: 100%;
    }

    .error-message {
      margin-top: 8px;
      color: #ff4d4f;
      font-size: 13px;
      display: flex;
      align-items: center;
      gap: 6px;

      span {
        display: inline;
      }
    }
  }
}
</style>
