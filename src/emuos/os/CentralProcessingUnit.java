/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package emuos.os

import java.util.BitSet
import java.util.Timer
import java.util.TimerTask

import emuos.generator.Instruction.*

/**
 * @author Link
 */
class CentralProcessingUnit {
    private val memoryManager = MemoryManager()
    private val processManager = ProcessManager(this, memoryManager)
    private val timer = Timer(false)
    /**
     * @return the AX
     */
    var ax: Int = 0
        private set
    /**
     * @return the PSW
     */
    var psw = BitSet(10)
        private set
    /**
     * @return the IR
     */
    var ir: Int = 0
        private set
    /**
     * @return the PC
     */
    var pc: Int = 0
        private set
    /**
     * @return the time
     */
    var time: Int = 0
        private set
    private var timeslice = 1

    fun CPU() {
        time++
        if (psw.get(PSW_INT_END)) {
            interruptEnd()
            psw.clear(PSW_INT_END)
        }
        if (psw.get(PSW_INT_TIME_SLICE)) {
            interruptTime()
            psw.clear(PSW_INT_TIME_SLICE)
        }
        if (psw.get(PSW_INT_IO)) {
            interruptIO()
            psw.clear(PSW_INT_IO)
        }

        ir = nextByte().toInt()
        execute()
        if (--timeslice == 0) {
            psw.set(PSW_INT_TIME_SLICE)
        }

        onFinished()
    }

    private fun onFinished() {
        System.out.printf(" CPU stat (Current Time: %d)\n", time)
        System.out.printf("TimeSlice = %d\n", timeslice)
        System.out.printf("AX = %d, PC = %d, PSW = %s\n", ax, pc, psw.toString())
        println("----------")
    }

    private fun nextByte(): Byte {
        return memoryManager.read(pc++)
    }

    private fun execute() {
        val opcode = ir.ushr(24)

        when (opcode) {
            OPCODE_END -> {
                psw.set(PSW_INT_END)
            }
            OPCODE_ASSIGNMENT -> {
                //int operand = IR & 0x000000ff;
                val operand = nextByte().toInt()
                ax = operand
                if (ax == 0) {
                    psw.set(PSW_ZF)
                }
                if (ax <= 0) {
                    psw.set(PSW_SF)
                }
            }
            OPCODE_INCREASE -> {
                ax = (ax + 1) % 0xff
                if (ax == 0) {
                    psw.set(PSW_ZF)
                }
            }
            OPCODE_DECREASE -> {
                ax = (ax - 1) % 0xff
                if (ax == 0) {
                    psw.set(PSW_ZF)
                }
            }
            OPCODE_IO -> {
                //int deviceID = (IR & 0x00ff0000) >> 16;
                //int usageTime = (IR & 0x0000ff00) >> 8;
                val deviceID = nextByte().toInt()
                val usageTime = nextByte().toInt()
                println("IO: $deviceID $usageTime")
            }
            else -> {
            }
        }

    }

    private fun interruptEnd() {

    }

    private fun interruptTime() {
        timeslice = 6
    }

    private fun interruptIO() {

    }

    fun run() {
        timer.schedule(object : TimerTask() {
            override fun run() {
                CPU()
            }
        }, 0, 500)
    }

    fun recoveryState(PCB: ProcessControlBlock) {
        this.ax = PCB.ax
        this.pc = PCB.pc
        this.psw = PCB.psw
    }

    interface Listener {
        fun on()
    }

    companion object {

        private val PSW_INT_END = 9
        private val PSW_INT_TIME_SLICE = 8
        private val PSW_INT_IO = 7
        private val PSW_CF = 3
        private val PSW_SF = 2
        private val PSW_OF = 1
        private val PSW_ZF = 0

        @JvmStatic fun main(args: Array<String>) {
            val CPU = CentralProcessingUnit()
            CPU.run()
        }
    }
}
