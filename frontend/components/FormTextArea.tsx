import React from "react";
import {useFormContext} from "react-hook-form";
import InputAssist from "@/components/InputAssist";

interface FormTextAreaProps  {
    id: string;
    rows?: number;
    cols?: number;
    className?: string;
    inputAssist?: boolean;
    inputAssistLang?: string;
}

export default function FormTextArea(props: FormTextAreaProps) {
    const {register} = useFormContext()

    function onKeyDown(e: React.KeyboardEvent) {
        if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
            e.currentTarget.closest('form')?.requestSubmit()
        }
    }

    return <>
        <textarea rows={props.rows} cols={props.cols} id={props.id} {...register(props.id)}
                  onKeyDown={onKeyDown} className={props.className}/>
        {props.inputAssist && <div><InputAssist id={props.id} language={props.inputAssistLang}/></div>}
    </>
}
