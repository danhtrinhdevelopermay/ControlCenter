document.addEventListener('DOMContentLoaded', function() {
    const buttons = {
        airplane: document.getElementById('airplane-btn'),
        cellular: document.getElementById('cellular-btn'),
        wifi: document.getElementById('wifi-btn'),
        bluetooth: document.getElementById('bluetooth-btn'),
        orientation: document.getElementById('orientation-btn'),
        mirror: document.getElementById('mirror-btn'),
        focus: document.getElementById('focus-btn'),
        flashlight: document.getElementById('flashlight-btn'),
        calculator: document.getElementById('calculator-btn'),
        recording: document.getElementById('recording-btn'),
        display: document.getElementById('display-btn'),
        hearing: document.getElementById('hearing-btn'),
        timer: document.getElementById('timer-btn'),
        battery: document.getElementById('battery-btn'),
        accessibility: document.getElementById('accessibility-btn')
    };

    const sliders = {
        brightness: document.getElementById('brightness-slider'),
        volume: document.getElementById('volume-slider')
    };

    function toggleButton(button, activeClass = 'active') {
        button.classList.toggle(activeClass);
        
        const rect = button.getBoundingClientRect();
        createRipple(rect.left + rect.width / 2, rect.top + rect.height / 2);
    }

    function createRipple(x, y) {
        const ripple = document.createElement('div');
        ripple.style.cssText = `
            position: fixed;
            left: ${x}px;
            top: ${y}px;
            width: 10px;
            height: 10px;
            background: rgba(255, 255, 255, 0.3);
            border-radius: 50%;
            transform: translate(-50%, -50%) scale(0);
            pointer-events: none;
            z-index: 9999;
        `;
        document.body.appendChild(ripple);
        
        ripple.animate([
            { transform: 'translate(-50%, -50%) scale(0)', opacity: 1 },
            { transform: 'translate(-50%, -50%) scale(4)', opacity: 0 }
        ], {
            duration: 400,
            easing: 'ease-out'
        }).onfinish = () => ripple.remove();
    }

    buttons.airplane.addEventListener('click', function() {
        toggleButton(this);
    });

    buttons.cellular.addEventListener('click', function() {
        toggleButton(this);
    });

    buttons.wifi.addEventListener('click', function() {
        toggleButton(this);
    });

    buttons.bluetooth.addEventListener('click', function() {
        if (this.classList.contains('bluetooth-active')) {
            this.classList.remove('bluetooth-active');
        } else if (this.classList.contains('active')) {
            this.classList.remove('active');
            this.classList.add('bluetooth-active');
        } else {
            this.classList.add('active');
        }
    });

    buttons.orientation.addEventListener('click', function() {
        toggleButton(this);
    });

    buttons.mirror.addEventListener('click', function() {
        toggleButton(this);
    });

    buttons.focus.addEventListener('click', function() {
        const focusIcon = this.querySelector('.focus-icon');
        const isActive = focusIcon.style.background === 'rgb(255, 59, 48)';
        focusIcon.style.background = isActive ? '#5E5CE6' : '#FF3B30';
    });

    [buttons.flashlight, buttons.calculator, buttons.recording, 
     buttons.hearing, buttons.timer, buttons.battery].forEach(btn => {
        btn.addEventListener('click', function() {
            toggleButton(this);
        });
    });

    buttons.display.addEventListener('click', function() {
        toggleButton(this);
    });

    buttons.accessibility.addEventListener('click', function() {
        toggleButton(this);
    });

    function handleSlider(slider, e) {
        const rect = slider.getBoundingClientRect();
        const y = e.clientY || (e.touches && e.touches[0].clientY);
        const percentage = Math.max(0, Math.min(100, ((rect.bottom - y) / rect.height) * 100));
        
        const fill = slider.querySelector('.slider-fill');
        fill.style.height = percentage + '%';
        
        const icon = slider.querySelector('.slider-icon');
        if (slider.id === 'volume-slider') {
            if (percentage === 0) {
                icon.classList.add('muted');
            } else {
                icon.classList.remove('muted');
            }
        }
    }

    Object.values(sliders).forEach(slider => {
        let isDragging = false;

        slider.addEventListener('mousedown', function(e) {
            isDragging = true;
            handleSlider(this, e);
        });

        slider.addEventListener('touchstart', function(e) {
            isDragging = true;
            handleSlider(this, e);
        }, { passive: true });

        document.addEventListener('mousemove', function(e) {
            if (isDragging) {
                handleSlider(slider, e);
            }
        });

        document.addEventListener('touchmove', function(e) {
            if (isDragging) {
                handleSlider(slider, e);
            }
        }, { passive: true });

        document.addEventListener('mouseup', function() {
            isDragging = false;
        });

        document.addEventListener('touchend', function() {
            isDragging = false;
        });
    });

    const hapticFeedback = (intensity = 'light') => {
        if ('vibrate' in navigator) {
            const durations = {
                light: 10,
                medium: 25,
                heavy: 50
            };
            navigator.vibrate(durations[intensity] || 10);
        }
    };

    document.querySelectorAll('button, .widget, .slider-widget').forEach(el => {
        el.addEventListener('touchstart', () => hapticFeedback('light'), { passive: true });
    });

    const connBtns = document.querySelectorAll('.conn-btn');
    connBtns.forEach(btn => {
        btn.addEventListener('contextmenu', function(e) {
            e.preventDefault();
            this.animate([
                { transform: 'scale(1)' },
                { transform: 'scale(1.1)' },
                { transform: 'scale(1)' }
            ], {
                duration: 300,
                easing: 'ease-out'
            });
            hapticFeedback('medium');
        });
    });

    function updateTime() {
        const now = new Date();
        const hours = now.getHours().toString().padStart(2, '0');
        const minutes = now.getMinutes().toString().padStart(2, '0');
    }

    updateTime();
    setInterval(updateTime, 1000);

    console.log('iOS Control Center initialized');
});
